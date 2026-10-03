#!/usr/bin/env python3
"""Optional RYZOD LAN DVR/download companion. Python 3.10+; ffmpeg for DVR.

Run: python3 server.py --bind 0.0.0.0 --port 8787 --data ./ryzod-data
Pair the printed address/token in Roku Settings. Never forward this port to the internet.
"""
from __future__ import annotations
import argparse
import contextlib
import hmac
import http.server
import json
import os
import re
import secrets
import shutil
import sqlite3
import subprocess
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from pathlib import Path

TERMINAL = {'complete','stopped','failed','paused'}
MAX_BODY = 65536


def parse_range(value: str, size: int) -> tuple[int,int]:
    match = re.fullmatch(r'bytes=(\d*)-(\d*)', value)
    if not match or size <= 0 or not any(match.groups()):
        raise ValueError('Invalid range')
    a,b=match.groups()
    if not a:
        suffix=int(b)
        if suffix<=0: raise ValueError('Invalid range')
        return max(0,size-suffix),size-1
    start=int(a);end=min(int(b),size-1) if b else size-1
    if start>=size or end<start: raise ValueError('Range outside file')
    return start,end


class Engine:
    def __init__(self, data: Path, token: str | None=None, startup_grace: float=12):
        self.data=Path(data).resolve();self.data.mkdir(parents=True,exist_ok=True)
        self.media=self.data/'media';self.media.mkdir(exist_ok=True)
        token_path=self.data/'pairing-token.txt'
        if token is None:
            if token_path.exists(): token=token_path.read_text().strip()
            else:
                token=secrets.token_urlsafe(24)
                token_path.write_text(token+'\n')
                with contextlib.suppress(OSError): token_path.chmod(0o600)
        if len(token)<16: raise ValueError('Use a pairing token of at least 16 characters')
        self.token=token
        self.lock=threading.RLock()
        self.db=sqlite3.connect(self.data/'jobs.sqlite3',check_same_thread=False)
        self.db.row_factory=sqlite3.Row
        self.db.execute('PRAGMA journal_mode=WAL')
        self.db.execute('''CREATE TABLE IF NOT EXISTS jobs (
            id TEXT PRIMARY KEY, kind TEXT NOT NULL, name TEXT NOT NULL, url TEXT NOT NULL,
            state TEXT NOT NULL, bytes INTEGER NOT NULL DEFAULT 0, total INTEGER NOT NULL DEFAULT 0,
            start INTEGER NOT NULL, duration INTEGER NOT NULL, format TEXT NOT NULL,
            resumable INTEGER NOT NULL, attempts INTEGER NOT NULL DEFAULT 0,
            next_retry REAL NOT NULL DEFAULT 0, created REAL NOT NULL, error TEXT NOT NULL DEFAULT '',
            etag TEXT NOT NULL DEFAULT '', modified TEXT NOT NULL DEFAULT '',
            agent TEXT NOT NULL DEFAULT '', scope TEXT NOT NULL DEFAULT '', max_streams INTEGER NOT NULL DEFAULT 1
        )''')
        self.db.execute("UPDATE jobs SET state='queued' WHERE kind='downloads' AND state='running'")
        self.db.execute("UPDATE jobs SET state='retry',next_retry=0,error='Companion restarted; retrying remaining recording.' WHERE kind='recordings' AND state='running'")
        self.db.commit()
        self.stop_event=threading.Event()
        self.scheduler=None
        self.workers:dict[str,tuple[threading.Thread,threading.Event]]= {}
        self.processes:dict[str,subprocess.Popen]={}
        self.responses={}
        self.closed=False
        self.leases={}
        self.revoked={}
        self.ready_at=time.time()+startup_grace

    def get(self, job_id:str)->dict:
        with self.lock:
            row=self.db.execute('SELECT * FROM jobs WHERE id=?',(job_id,)).fetchone()
        if row is None: raise KeyError('Job not found')
        return dict(row)

    def list(self, public=True)->list[dict]:
        with self.lock:
            rows=[dict(r) for r in self.db.execute('SELECT * FROM jobs ORDER BY created,id')]
        if public:
            for row in rows:
                for key in ['url','agent','scope','etag','modified','max_streams']:
                    row.pop(key,None)
                row['resumable']=bool(row['resumable'])
        return rows

    def update(self,job_id:str,**fields):
        allowed={'state','bytes','total','attempts','next_retry','error','etag','modified'}
        if not fields or not fields.keys()<=allowed: raise ValueError('Invalid job fields')
        with self.lock:
            query='UPDATE jobs SET '+','.join(f'{key}=?' for key in fields)+' WHERE id=?'
            self.db.execute(query,(*fields.values(),job_id));self.db.commit()

    def media_path(self,job_id:str)->Path:
        job=self.get(job_id)
        ext=job['format']
        if ext not in {'mp4','mkv','ts','hls','dash'}: ext='mp4'
        if ext in {'hls','dash'}: ext='ts'
        return self.media/(job['id']+'.'+ext)

    def add(self,body:dict)->dict:
        if not isinstance(body,dict): raise ValueError('Expected job object')
        if len(self.list(False))>=500: raise ValueError('Remove old jobs before adding more')
        kind=body.get('kind');url=body.get('url','');name=body.get('name','')
        if kind not in {'downloads','recordings'}: raise ValueError('Invalid job kind')
        if not isinstance(url,str) or len(url)>4096: raise ValueError('Invalid source address')
        parsed=urllib.parse.urlsplit(url)
        if parsed.scheme not in {'http','https'} or not parsed.hostname or any(c in url for c in '\r\n '):
            raise ValueError('Use a valid http/https source address')
        if not isinstance(name,str) or not name.strip() or len(name)>256: raise ValueError('Invalid media name')
        now=int(time.time())
        try:
            start=int(body.get('start',now));duration=int(body.get('duration',0));streams=int(body.get('maxStreams',1))
        except (ValueError,TypeError,OverflowError): raise ValueError('Invalid schedule') from None
        if start>now+604800: raise ValueError('Schedule must start within seven days')
        if start<now-60: raise ValueError('Start time is in the past')
        if kind=='recordings' and not 1<=duration<=43200: raise ValueError('Duration must be 1 second to 12 hours')
        if kind=='downloads':duration=0;start=now
        if not 1<=streams<=3: raise ValueError('Provider stream limit must be 1, 2 or 3')
        agent=body.get('agent','')
        if not isinstance(agent,str) or len(agent)>512 or '\r' in agent or '\n' in agent:raise ValueError('Invalid User-Agent')
        scope=body.get('scope','')
        if not isinstance(scope,str) or len(scope)>4096: raise ValueError('Invalid playlist scope')
        extension=parsed.path.lower().rsplit('.',1)[-1]
        fmt=body.get('format') or ('hls' if extension=='m3u8' else 'dash' if extension=='mpd' else extension)
        if fmt not in {'mp4','mkv','ts','hls','dash'}:fmt='mp4'
        if kind=='recordings' or fmt in {'hls','dash'}:fmt='ts'
        # Playlist manifests cannot be byte-resumed; their download restarts cleanly.
        resumable=kind=='downloads' and extension not in {'m3u8','mpd'}
        state='scheduled' if start>now else 'queued'
        job_id=uuid.uuid4().hex
        with self.lock:
            self.db.execute('''INSERT INTO jobs(id,kind,name,url,state,start,duration,format,resumable,created,agent,scope,max_streams)
              VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)''',(job_id,kind,name.strip(),url,state,start,duration,fmt,int(resumable),time.time(),agent,scope,streams))
            self.db.commit()
        path=self.media_path(job_id);path.touch()
        return self.public_job(job_id)

    def public_job(self,job_id):
        return next(j for j in self.list() if j['id']==job_id)

    def action(self,job_id:str,action:str)->dict:
        with self.lock:
            job=self.get(job_id)
            if action=='delete':
                self.cancel(job_id)
                worker=self.workers.get(job_id)
            else: worker=None
            if action=='pause':
                if job['kind']!='downloads':raise ValueError('Only downloads can be paused')
                if job['state']=='complete':raise ValueError('Download is already complete')
                self.update(job_id,state='paused');self.cancel(job_id)
            elif action=='stop':
                self.update(job_id,state='stopped');self.cancel(job_id)
            elif action=='resume':
                if job['state']=='complete':raise ValueError('Job is already complete')
                if job['kind']=='recordings' and time.time()>=job['start']+job['duration']:raise ValueError('Recording time has passed')
                self.update(job_id,state='queued',attempts=0,next_retry=0,error='')
            elif action!='delete':raise ValueError('Unknown action')
        if action=='delete':
            # Wait outside the DB lock. A worker may be persisting its final bytes.
            if worker:
                worker[0].join(20)
                if worker[0].is_alive():raise ValueError('Job is still stopping; try deleting again')
            path=self.media_path(job_id)
            path.unlink(missing_ok=True)
            with self.lock:
                self.db.execute('DELETE FROM jobs WHERE id=?',(job_id,));self.db.commit()
            return {'deleted':True,'id':job_id}
        return self.public_job(job_id)

    def cancel(self,job_id):
        worker=self.workers.get(job_id)
        if worker:worker[1].set()
        process=self.processes.get(job_id)
        if process and process.poll() is None:
            with contextlib.suppress(OSError):process.terminate()

    def playback(self,body:dict)->dict:
        if not isinstance(body,dict):raise ValueError('Expected reservation object')
        client=body.get('client','');scope=body.get('scope','')
        if not isinstance(client,str) or not 1<=len(client)<=128:raise ValueError('Invalid client')
        if not isinstance(scope,str) or not scope or len(scope)>4096:raise ValueError('Invalid playlist scope')
        try:limit=int(body.get('maxStreams',1))
        except (ValueError,TypeError):raise ValueError('Invalid stream count') from None
        if limit not in {1,2,3}:raise ValueError('Invalid stream count')
        session=body.get('session',client)
        if not isinstance(session,str) or not 1<=len(session)<=128:raise ValueError('Invalid playback session')
        with self.lock:
            now=time.time()
            self.revoked={k:v for k,v in self.revoked.items() if v>now}
            key=(client,session)
            if body.get('action')=='release':
                self.revoked[key]=now+30
                lease=self.leases.get(client)
                if lease and lease['session']==session:self.leases.pop(client,None)
                return {'allowed':False,'released':True,'ttl':0}
            if key in self.revoked:return {'allowed':False,'ttl':0,'reason':'Playback session was stopped.'}
            self.leases={k:v for k,v in self.leases.items() if v['until']>now}
            others=[v for k,v in self.leases.items() if k!=client and v['scope']==scope]
            # Reserve capacity before a scheduled recording; a refused renewal leaves
            # the previous lease until expiry, giving Roku time to stop its Video.
            jobs=[j for j in self.list(False) if j['scope']==scope and
                (j['id'] in self.workers or (j['state'] in {'queued','scheduled','retry'} and
                j['start']<=now+15 and (j['kind']=='downloads' or j['start']+j['duration']>now)))]
            limit=min([limit]+[j['max_streams'] for j in jobs]+[v['limit'] for v in others])
            allowed=len(others)+len(jobs)<limit
            if allowed:self.leases[client]={'scope':scope,'limit':limit,'until':now+10,'session':session}
            return {'allowed':allowed,'ttl':10,'reason':'' if allowed else 'Provider streams reserved for a recording or download.'}

    def start(self):
        if self.scheduler is not None:return
        self.scheduler=threading.Thread(target=self._schedule,name='ryzod-scheduler',daemon=True)
        self.scheduler.start()

    def _schedule(self):
        while not self.stop_event.wait(.05):
            with self.lock:
                for job_id,(thread,event) in list(self.workers.items()):
                    if not thread.is_alive():self.workers.pop(job_id,None)
                if time.time()<self.ready_at:continue
                self.leases={k:v for k,v in self.leases.items() if v['until']>time.time()}
                jobs=self.list(False)
                running=[j for j in jobs if j['id'] in self.workers]
                for job in sorted(jobs,key=lambda j:(j['kind']!='recordings',j['start'],j['created'])):
                    if job['id'] in self.workers:continue
                    if job['state'] not in {'queued','scheduled','retry'}:continue
                    now=time.time()
                    if job['start']>now or job['next_retry']>now:continue
                    if job['kind']=='recordings' and job['start']+job['duration']<=now:
                        self.update(job['id'],state='stopped',error='Recording time ended before a stream became available.')
                        continue
                    scope_running=[j for j in running if j['scope']==job['scope']]
                    # One active download globally; DVR slots honor each provider scope.
                    if job['kind']=='downloads' and any(j['kind']=='downloads' for j in running):continue
                    leases=[v for v in self.leases.values() if v['scope']==job['scope']]
                    limit=min([job['max_streams']]+[j['max_streams'] for j in scope_running]+[v['limit'] for v in leases])
                    if len(scope_running)+len(leases)>=limit:
                        if job['kind']=='recordings':
                            for active in scope_running:
                                if active['kind']=='downloads':
                                    self.update(active['id'],state='queued');self.cancel(active['id'])
                                    break
                        continue
                    event=threading.Event()
                    thread=threading.Thread(target=self._work,args=(job['id'],event),name='ryzod-'+job['id'][:8],daemon=True)
                    self.workers[job['id']]=(thread,event)
                    self.update(job['id'],state='running',attempts=job['attempts']+1,error='')
                    running.append(job);thread.start()

    def _work(self,job_id,event):
        try:
            job=self.get(job_id)
            is_manifest=urllib.parse.urlsplit(job['url']).path.lower().endswith(('.m3u8','.mpd'))
            if job['kind']=='recordings' or is_manifest:self._ffmpeg(job,event)
            else:self._download(job,event)
            if not event.is_set() and not self.stop_event.is_set():
                self.update(job_id,state='complete',bytes=self.media_path(job_id).stat().st_size,error='')
        except Exception:
            # Never return network/ffmpeg exception text: it can contain provider credentials.
            with self.lock:
                with contextlib.suppress(KeyError):
                    job=self.get(job_id)
                    if job['state']=='running' and not event.is_set() and not self.stop_event.is_set():
                        state='retry'
                        if job['kind']=='downloads' and job['attempts']>=5:state='failed'
                        if job['kind']=='recordings' and job['start']+job['duration']<=time.time():state='stopped'
                        self.update(job_id,state=state,next_retry=time.time()+min(60,2**min(job['attempts'],6)),bytes=self.media_path(job_id).stat().st_size,error='Source unavailable or incompatible. Retry will preserve supported partial downloads.')
        finally:
            with self.lock:
                self.processes.pop(job_id,None);self.responses.pop(job_id,None)

    def _download(self,job,event):
        path=self.media_path(job['id']);offset=path.stat().st_size
        headers={'User-Agent':job['agent'] or 'RYZOD-Companion/0.1.0','Accept-Encoding':'identity'}
        if offset:
            headers['Range']=f'bytes={offset}-'
            validator=job['etag'] or job['modified']
            if validator:headers['If-Range']=validator
        req=urllib.request.Request(job['url'],headers=headers)
        try:response=urllib.request.urlopen(req,timeout=10)
        except urllib.error.HTTPError as error:
            if error.code==416:
                match=re.fullmatch(r'bytes \*/(\d+)',error.headers.get('Content-Range',''))
                unchanged=not job['etag'] or error.headers.get('ETag',job['etag'])==job['etag']
                if match and offset==int(match[1]) and unchanged:
                    self.update(job['id'],bytes=offset,total=offset);return
                path.write_bytes(b'');raise ValueError('Range no longer valid') from None
            raise
        with response:
            with self.lock:self.responses[job['id']]=response
            total=0;mode='wb'
            if response.status==206:
                match=re.fullmatch(r'bytes (\d+)-(\d+)/(\d+)',response.headers.get('Content-Range',''))
                if not match or int(match[1])!=offset or int(match[2])<offset:raise ValueError('Invalid Content-Range')
                if job['etag'] and response.headers.get('ETag',job['etag'])!=job['etag']:
                    path.write_bytes(b'');raise ValueError('Source changed')
                total=int(match[3]);mode='ab'
            elif response.status==200:
                offset=0;total=int(response.headers.get('Content-Length','0'))
            else:raise ValueError('Invalid source response')
            etag=response.headers.get('ETag','')
            if etag.startswith('W/'):etag=''
            self.update(job['id'],bytes=offset,total=total,etag=etag,modified=response.headers.get('Last-Modified',''))
            written=offset
            with path.open(mode) as output:
                while not event.is_set() and not self.stop_event.is_set():
                    chunk=response.read(16384)
                    if not chunk:break
                    output.write(chunk);output.flush();written+=len(chunk)
                    self.update(job['id'],bytes=written)
            if not event.is_set() and not self.stop_event.is_set() and total and written!=total:raise ValueError('Incomplete response')

    def _ffmpeg(self,job,event):
        binary=shutil.which('ffmpeg')
        if not binary:raise ValueError('ffmpeg is required for recordings and HLS downloads')
        path=self.media_path(job['id'])
        remaining=job['start']+job['duration']-time.time()
        if job['kind']=='recordings' and remaining<=0:return
        args=[binary,'-nostdin','-hide_banner','-loglevel','error','-rw_timeout','10000000','-user_agent',job['agent'] or 'RYZOD-Companion/0.1.0','-i',job['url']]
        if job['kind']=='recordings':args+=['-t',str(max(1,remaining))]
        args+=['-c','copy','-f','mpegts','pipe:1']
        mode='ab' if job['kind']=='recordings' else 'wb'
        process=subprocess.Popen(args,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL)
        with self.lock:self.processes[job['id']]=process
        try:
            with path.open(mode) as output:
                while not event.is_set() and not self.stop_event.is_set():
                    chunk=process.stdout.read(16384)
                    if not chunk:break
                    output.write(chunk);output.flush()
                    self.update(job['id'],bytes=output.tell())
            if event.is_set() or self.stop_event.is_set():
                with contextlib.suppress(OSError):process.terminate()
            code=process.wait(timeout=15)
            if code and not event.is_set() and not self.stop_event.is_set():raise ValueError('Recording failed')
            if path.stat().st_size==0 and not event.is_set():raise ValueError('No playable media saved')
        finally:
            if process.poll() is None:
                process.kill();process.wait(5)
            process.stdout.close()

    def close(self):
        if self.closed:return
        self.stop_event.set()
        with self.lock:
            workers=list(self.workers.items())
            for job_id,_ in workers:self.cancel(job_id)
        if self.scheduler:self.scheduler.join(3)
        for _,(thread,_) in workers:thread.join(20)
        with self.lock:
            self.db.close();self.closed=True


def create_server(engine:Engine,bind='127.0.0.1',port=8787):
    class Handler(http.server.BaseHTTPRequestHandler):
        server_version='RYZOD-Companion/0.1.0'
        def log_message(self,*args):pass
        def setup(self):
            super().setup();self.connection.settimeout(10)
        def authorized(self):
            return hmac.compare_digest(self.headers.get('Authorization',''),'Bearer '+engine.token)
        def json_response(self,status,body):
            data=json.dumps(body,separators=(',',':')).encode()
            self.send_response(status);self.send_header('Content-Type','application/json');self.send_header('Content-Length',str(len(data)));self.send_header('Cache-Control','no-store');self.end_headers();self.wfile.write(data)
        def do_GET(self):
            if not self.authorized():self.json_response(401,{'error':'Pairing token required'});return
            path=urllib.parse.urlsplit(self.path).path
            if path=='/api/status':self.json_response(200,{'ok':True,'version':'0.1.0','ffmpeg':bool(shutil.which('ffmpeg'))})
            elif path=='/api/jobs':self.json_response(200,{'jobs':engine.list()})
            elif path.startswith('/media/'):
                try:self.serve_media(path[7:])
                except KeyError:self.json_response(404,{'error':'Media not found'})
            else:self.json_response(404,{'error':'Not found'})
        def do_HEAD(self):
            self.do_GET()
        def do_POST(self):
            if not self.authorized():self.json_response(401,{'error':'Pairing token required'});return
            try:
                size=int(self.headers.get('Content-Length','0'))
                if not 0<size<=MAX_BODY:raise ValueError('Invalid request size')
                body=json.loads(self.rfile.read(size))
                path=urllib.parse.urlsplit(self.path).path
                if path=='/api/playback':self.json_response(200,engine.playback(body));return
                if path=='/api/jobs':self.json_response(201,{'job':engine.add(body)});return
                match=re.fullmatch(r'/api/jobs/([a-f0-9]{32})/(pause|resume|stop|delete)',path)
                if match:self.json_response(200,{'job':engine.action(*match.groups())});return
                self.json_response(404,{'error':'Not found'})
            except (ValueError,TypeError,json.JSONDecodeError):self.json_response(400,{'error':'Invalid job, source or schedule'})
            except KeyError:self.json_response(404,{'error':'Job not found'})
        def serve_media(self,job_id):
            job=engine.get(job_id);file=engine.media_path(job_id)
            if not file.exists():raise KeyError('Media missing')
            size=file.stat().st_size
            if size==0:self.json_response(409,{'error':'No media saved yet'});return
            start,end=0,size-1;status=200
            if self.headers.get('Range'):
                try:start,end=parse_range(self.headers['Range'],size);status=206
                except ValueError:
                    self.send_response(416);self.send_header('Content-Range',f'bytes */{size}');self.send_header('Content-Length','0');self.end_headers();return
            self.send_response(status)
            self.send_header('Content-Type',{'mp4':'video/mp4','mkv':'video/x-matroska','ts':'video/mp2t','hls':'video/mp2t','dash':'video/mp2t'}.get(job['format'],'application/octet-stream'))
            self.send_header('Accept-Ranges','bytes');self.send_header('Content-Length',str(end-start+1));self.send_header('Cache-Control','no-store')
            if status==206:self.send_header('Content-Range',f'bytes {start}-{end}/{size}')
            self.end_headers()
            if self.command=='HEAD':return
            try:
                with file.open('rb') as stream:
                    stream.seek(start);left=end-start+1
                    while left>0:
                        data=stream.read(min(65536,left))
                        if not data:break
                        self.wfile.write(data);left-=len(data)
            except (BrokenPipeError,ConnectionResetError,TimeoutError):pass
    return http.server.ThreadingHTTPServer((bind,port),Handler)


def main():
    parser=argparse.ArgumentParser(description=__doc__,formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--bind',default='0.0.0.0');parser.add_argument('--port',type=int,default=8787);parser.add_argument('--data',type=Path,default=Path('ryzod-data'))
    args=parser.parse_args();engine=Engine(args.data);server=create_server(engine,args.bind,args.port);engine.start()
    print(f'RYZOD companion listening on port {server.server_port}. Pair using your computer LAN address.')
    print(f'Pairing token: {engine.token}')
    print('Recordings/HLS downloads require ffmpeg. Keep this computer powered on. Keep access on your home network.')
    try:server.serve_forever()
    except KeyboardInterrupt:pass
    finally:server.server_close();engine.close()

if __name__=='__main__':main()
