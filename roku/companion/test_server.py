import http.server
import json
import tempfile
import subprocess
import shutil
import threading
import time
import unittest
import urllib.error
import urllib.request
from pathlib import Path

try:
    from server import Engine, create_server, parse_range
except ImportError:
    Engine = create_server = parse_range = None

DATA = bytes(range(256)) * 4096

class Fixture(http.server.BaseHTTPRequestHandler):
    requests = []
    slow = False
    ignore_range = False
    fail = False
    payload = DATA
    def do_GET(self):
        self.__class__.requests.append(self.headers.get('Range'))
        if self.__class__.fail:
            self.send_error(503)
            return
        data=self.__class__.payload
        offset = 0
        if self.headers.get('Range') and not self.__class__.ignore_range:
            offset = int(self.headers['Range'].split('=')[1].split('-')[0])
            self.send_response(206)
            self.send_header('Content-Range', f'bytes {offset}-{len(data)-1}/{len(data)}')
        else:
            self.send_response(200)
        self.send_header('Content-Length', str(len(data)-offset))
        self.send_header('ETag', '"fixture-v1"')
        self.end_headers()
        try:
            for i in range(offset,len(data),4096):
                self.wfile.write(data[i:i+4096])
                self.wfile.flush()
                if self.__class__.slow:
                    time.sleep(.003)
        except (BrokenPipeError,ConnectionResetError):
            pass
    def log_message(self,*args): pass

class CompanionTests(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(Engine,'Companion implementation is missing')
        self.tmp=tempfile.TemporaryDirectory()
        self.root=Path(self.tmp.name)
        Fixture.payload=DATA;Fixture.requests=[]; Fixture.slow=False; Fixture.ignore_range=False; Fixture.fail=False
        self.fixture=http.server.ThreadingHTTPServer(('127.0.0.1',0),Fixture)
        self.fixture_thread=threading.Thread(target=self.fixture.serve_forever,daemon=True);self.fixture_thread.start()
        self.url=f'http://127.0.0.1:{self.fixture.server_port}/film.mp4'
        self.engine=Engine(self.root,token='x'*32,startup_grace=0)
        self.api=create_server(self.engine,'127.0.0.1',0)
        self.api_thread=threading.Thread(target=self.api.serve_forever,daemon=True);self.api_thread.start()
        self.base=f'http://127.0.0.1:{self.api.server_port}'
    def tearDown(self):
        if hasattr(self,'engine'): self.engine.close()
        if hasattr(self,'api'): self.api.shutdown();self.api.server_close();self.api_thread.join(2)
        if hasattr(self,'fixture'): self.fixture.shutdown();self.fixture.server_close();self.fixture_thread.join(2)
        if hasattr(self,'tmp'): self.tmp.cleanup()
    def call(self,path,body=None,token=True,headers=None):
        h=dict(headers or {})
        if token: h['Authorization']='Bearer '+self.engine.token
        if body is not None: h['Content-Type']='application/json'
        req=urllib.request.Request(self.base+path,data=None if body is None else json.dumps(body).encode(),headers=h)
        return urllib.request.urlopen(req,timeout=3)
    def add(self,kind='downloads',**extra):
        body={'name':'Test media','kind':kind,'url':self.url,'scope':'p1','maxStreams':1}
        body.update(extra)
        return self.engine.add(body)
    def until(self,job_id,states,timeout=6):
        deadline=time.monotonic()+timeout
        while time.monotonic()<deadline:
            job=self.engine.get(job_id)
            if job['state'] in states:return job
            time.sleep(.015)
        self.fail(f'Job never reached {states}: {self.engine.get(job_id)}')
    def test_playback_reservation_denies_active_job_and_yields_before_schedule(self):
        result=self.engine.playback({'client':'roku-a','scope':'p1','maxStreams':1})
        self.assertTrue(result['allowed'])
        job=self.add()
        Fixture.slow=True
        self.engine.start()
        time.sleep(.12)
        self.assertEqual(self.engine.get(job['id'])['state'],'queued')
        self.engine.leases.clear()
        self.until(job['id'],{'running'})
        self.assertFalse(self.engine.playback({'client':'roku-a','scope':'p1','maxStreams':1})['allowed'])
        self.engine.action(job['id'],'stop')
        self.until(job['id'],{'stopped'})
        self.engine.workers[job['id']][0].join(2)
        self.engine.workers.clear()
        self.add('recordings',start=int(time.time())+8,duration=30,maxStreams=2)
        self.assertFalse(self.engine.playback({'client':'roku-a','scope':'p1','maxStreams':1})['allowed'])
        with self.call('/api/playback',{'client':'roku-a','scope':'p1','maxStreams':2}) as res:
            self.assertTrue(json.load(res)['allowed'])
        with self.assertRaises(urllib.error.HTTPError):self.call('/api/playback',{'client':'roku-a','scope':'p1'},token=False)
    def test_playback_leases_expire_and_restart_has_quiet_period(self):
        self.engine.playback({'client':'roku-a','scope':'p1','maxStreams':1})
        self.engine.leases['roku-a']['until']=time.time()-1
        self.assertTrue(self.engine.playback({'client':'roku-b','scope':'p1','maxStreams':1})['allowed'])
        restarted=Engine(self.root/'restart',token='x'*32)
        try:self.assertGreater(restarted.ready_at,time.time()+9)
        finally:restarted.close()
    def test_release_revokes_late_renewal_without_removing_new_playback(self):
        a={'client':'roku-a','scope':'p1','maxStreams':1,'session':'first'}
        self.assertTrue(self.engine.playback(a)['allowed'])
        self.engine.playback(dict(a,action='release'))
        self.assertNotIn('roku-a',self.engine.leases)
        b=dict(a,session='second')
        self.assertTrue(self.engine.playback(b)['allowed'])
        self.assertFalse(self.engine.playback(a)['allowed'])
        self.engine.playback(dict(a,action='release'))
        self.assertEqual(self.engine.leases['roku-a']['session'],'second')
    def test_range_parser_supports_suffix_open_and_rejects_unsatisfiable(self):
        self.assertEqual(parse_range('bytes=2-4',10),(2,4))
        self.assertEqual(parse_range('bytes=4-',10),(4,9))
        self.assertEqual(parse_range('bytes=-3',10),(7,9))
        for value in ['bytes=12-','bytes=4-2','bytes=0-1,3-4','other=1-2']:
            with self.assertRaises(ValueError): parse_range(value,10)
    def test_api_and_media_require_token_and_do_not_leak_source_urls(self):
        for path in ['/api/status','/api/jobs','/media/anything']:
            with self.assertRaises(urllib.error.HTTPError) as error:self.call(path,token=False)
            self.assertEqual(error.exception.code,401)
        self.add()
        response=json.load(self.call('/api/jobs'))
        self.assertNotIn('url',response['jobs'][0]); self.assertNotIn(self.url,json.dumps(response))
    def test_create_api_validates_schedule_and_source(self):
        for body in [dict(kind='downloads',name='x',url='file:///etc/passwd'),dict(kind='recordings',name='x',url=self.url,start=time.time()+604900,duration=60),dict(kind='recordings',name='x',url=self.url,duration=0)]:
            with self.assertRaises(urllib.error.HTTPError) as error:self.call('/api/jobs',body)
            self.assertEqual(error.exception.code,400)
        with self.call('/api/jobs',dict(kind='downloads',name='x',url=self.url)) as response:
            self.assertEqual(response.status,201)
    def test_future_schedule_survives_restart_without_starting_early(self):
        job=self.add('recordings',start=int(time.time())+300,duration=60)
        self.engine.close()
        self.engine=Engine(self.root,token='x'*32,startup_grace=0)
        self.assertEqual(self.engine.get(job['id'])['state'],'scheduled')
        self.engine.start();time.sleep(.08)
        self.assertEqual(self.engine.get(job['id'])['state'],'scheduled')
    def test_download_completes_with_exact_bytes_and_authenticated_range_playback(self):
        job=self.add();self.engine.start();done=self.until(job['id'],{'complete'})
        self.assertEqual(done['bytes'],len(DATA));self.assertEqual(self.engine.media_path(job['id']).read_bytes(),DATA)
        with self.call('/media/'+job['id'],headers={'Range':'bytes=123-456'}) as response:
            self.assertEqual(response.status,206);self.assertEqual(response.read(),DATA[123:457]);self.assertEqual(response.headers['Content-Range'],f'bytes 123-456/{len(DATA)}')
    def test_pausing_download_keeps_partial_and_queue_runs_next(self):
        Fixture.slow=True
        a=self.add();b=self.add(name='Second')
        self.engine.start();self.until(a['id'],{'running'})
        deadline=time.monotonic()+2
        while self.engine.media_path(a['id']).stat().st_size<16384 and time.monotonic()<deadline:time.sleep(.01)
        self.engine.action(a['id'],'pause'); self.until(a['id'],{'paused'})
        self.until(b['id'],{'complete'})
        partial=self.engine.media_path(a['id']).stat().st_size
        self.assertGreater(partial,0);self.assertLess(partial,len(DATA))
        self.engine.action(a['id'],'resume');self.until(a['id'],{'complete'})
        self.assertEqual(self.engine.media_path(a['id']).read_bytes(),DATA)
        self.assertTrue(any(r is not None for r in Fixture.requests))
    def test_server_ignoring_range_restarts_without_corrupt_append(self):
        job=self.add();self.engine.media_path(job['id']).write_bytes(DATA[:20000])
        Fixture.ignore_range=True;self.engine.start();self.until(job['id'],{'complete'})
        self.assertEqual(self.engine.media_path(job['id']).read_bytes(),DATA)
    def test_failure_retries_without_losing_partial_and_can_resume(self):
        Fixture.fail=True;job=self.add();self.engine.start();failed=self.until(job['id'],{'retry','failed'})
        self.assertGreater(failed['attempts'],0)
        Fixture.fail=False;self.engine.action(job['id'],'resume');self.until(job['id'],{'complete'})
        self.assertEqual(self.engine.media_path(job['id']).read_bytes(),DATA)
    def test_stop_and_delete_are_durable_and_path_traversal_cannot_select_media(self):
        job=self.add('recordings',start=int(time.time())+300,duration=60)
        self.engine.action(job['id'],'stop');self.assertEqual(self.engine.get(job['id'])['state'],'stopped')
        self.engine.action(job['id'],'delete')
        with self.assertRaises(KeyError):self.engine.get(job['id'])
        with self.assertRaises(KeyError):self.engine.media_path('../../etc/passwd')
    def test_companion_restart_requeues_incomplete_download(self):
        job=self.add();self.engine.media_path(job['id']).write_bytes(DATA[:10000])
        self.engine.update(job['id'],state='running',bytes=10000)
        self.engine.close();self.engine=Engine(self.root,token='x'*32,startup_grace=0)
        self.assertEqual(self.engine.get(job['id'])['state'],'queued')
        self.engine.start();self.until(job['id'],{'complete'})
        self.assertEqual(self.engine.media_path(job['id']).read_bytes(),DATA)
    def test_single_stream_scope_never_starts_two_jobs_at_once(self):
        Fixture.slow=True;a=self.add();b=self.add()
        self.engine.start();self.until(a['id'],{'running'})
        self.assertEqual(self.engine.get(b['id'])['state'],'queued')
        self.until(a['id'],{'complete'});self.until(b['id'],{'complete'})

    @unittest.skipUnless(shutil.which('ffmpeg'), 'ffmpeg required for real DVR fixture')
    def test_recording_worker_produces_playable_transport_stream(self):
        video=self.root/'fixture.mp4'
        subprocess.run(['ffmpeg','-nostdin','-hide_banner','-loglevel','error','-f','lavfi','-i','color=c=blue:s=160x90:r=15','-f','lavfi','-i','sine=frequency=440:sample_rate=44100','-t','1','-c:v','libx264','-pix_fmt','yuv420p','-c:a','aac','-movflags','+faststart',str(video)],check=True,timeout=15)
        Fixture.payload=video.read_bytes()
        job=self.add('recordings',duration=3)
        self.engine.start();self.until(job['id'],{'complete'},timeout=10)
        path=self.engine.media_path(job['id'])
        self.assertGreater(path.stat().st_size,1000)
        probe=subprocess.run(['ffprobe','-v','error','-show_entries','stream=codec_type','-of','json',str(path)],capture_output=True,text=True,check=True,timeout=10)
        kinds={s['codec_type'] for s in json.loads(probe.stdout)['streams']}
        self.assertEqual(kinds,{'audio','video'})
    def test_head_and_unsatisfiable_media_ranges(self):
        job=self.add();self.engine.media_path(job['id']).write_bytes(DATA)
        request=urllib.request.Request(self.base+'/media/'+job['id'],method='HEAD',headers={'Authorization':'Bearer '+self.engine.token})
        with urllib.request.urlopen(request,timeout=3) as response:
            self.assertEqual(int(response.headers['Content-Length']),len(DATA));self.assertEqual(response.read(),b'')
        with self.assertRaises(urllib.error.HTTPError) as error:
            self.call('/media/'+job['id'],headers={'Range':'bytes=9999999-'})
        self.assertEqual(error.exception.code,416)
