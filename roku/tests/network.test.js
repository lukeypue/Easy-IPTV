const {test}=require('node:test');
const assert=require('node:assert/strict');
const http=require('node:http');
const fs=require('node:fs');
const path=require('node:path');
const brs=require('brs-node');
const root=path.resolve(__dirname,'..');
test('real Task worker authenticates, loads wrapped catalog and guide, and returns HTTP failures', {timeout:30000}, async()=>{
 let requests=[];
 const server=http.createServer((req,res)=>{
   const u=new URL(req.url,'http://local');requests.push(u);
   res.setHeader('Content-Type','application/json');
   if(u.pathname==='/api/playback'){
    assert.equal(req.headers.authorization,'Bearer pairing-token');
    let raw='';req.on('data',d=>raw+=d);req.on('end',()=>{assert.equal(JSON.parse(raw).client,'roku-test');res.end('{"allowed":true,"ttl":10}');});return;
   }
   const a=u.searchParams.get('action');
   if(u.pathname==='/bad/player_api.php'){res.writeHead(503);res.end('{}');return;}
   let response={user_info:{auth:1}};
   if(a==='get_live_categories')response=[{category_id:2,category_name:'News'}];
   if(a==='get_live_streams')response={data:[{stream_id:10,name:'News',category_id:2}]};
   if(a==='get_short_epg')response={epg_listings:[{start_timestamp:1000,stop_timestamp:8200,title:'TmV3cw=='}]};
   res.end(JSON.stringify(response));
 });
 await new Promise(r=>server.listen(0,'127.0.0.1',r));
 const url=`http://127.0.0.1:${server.address().port}`;
 const map=new Map();
 for(const file of ['manifest','source/core/Provider.brs','source/core/Guide.brs','components/RequestTask.xml','components/RequestTask.brs'])map.set(file,new Blob([fs.readFileSync(path.join(root,file))]));
 map.set('source/main.brs',new Blob([`sub Main()
 screen=createObject("roSGScreen")
 scene=screen.createScene("NetScene")
 screen.show()
 p={type:"xtream",host:"${url}",user:"u/a",pass:"p?&"}
 r=fetch({id:1,generation:3,kind:"connect",playlist:p})
 expect(r.ok,"auth")
 r=fetch({id:2,generation:3,kind:"catalog",mediaKind:"live",category:"all",playlist:p})
 expect(r.ok,"catalog")
 if r.ok
 if r.data<>invalid then expect(r.data.items[0].title="News","wrapped rows")
 end if
 r=fetch({id:3,generation:3,kind:"epg",channelId:"10",playlist:p})
 expect(r.ok,"epg")
 if r.ok then expect(r.data[0].end-r.data[0].start=7200,"guide duration")
 p.host="${url}/bad"
 r=fetch({id:4,generation:3,kind:"connect",playlist:p})
 expect(not r.ok,"HTTP fail")
 expect(r.generation=3 and r.id=4,"envelope")
 r=fetch({id:5,generation:3,kind:"playbackLease",playlist:p,companion:"${url}",token:"pairing-token",reservation:{client:"roku-test",scope:"p1",maxStreams:1}})
 expect(r.ok,"lease request")
 if r.ok then expect(r.data.allowed,"lease approval")
 print "TASK_PASSED"
 screen.close()
end sub
function fetch(request as object) as object
 port=createObject("roMessagePort")
 task=createObject("roSGNode","RequestTask")
 task.observeField("response",port)
 task.request=request
 task.control="run"
 msg=wait(5000,port)
 if type(msg)="roSGNodeEvent" then return msg.getData()
 return {ok:false,error:"timeout",generation:0,id:0}
end function
sub expect(ok as boolean,message as string)
 if not ok then print "TASK_FAILED: ";message
end sub
`]));
 map.set('components/NetScene.xml',new Blob(['<component name="NetScene" extends="Scene"><children /></component>']));
 let log='';brs.subscribeHost('test',(event,data)=>{if(event==='message' && typeof data==='string'){log+=data+'\n';if(data.startsWith('error')||data.startsWith('warning'))console.error(data);}});
 try{const payload=await brs.createPayloadFromFileMap(map,{developerId:'ryzod-network-test',localIps:['127.0.0.1']});payload.extensions=[brs.SupportedExtension.SceneGraph];payload.device.extensions=new Map([[brs.SupportedExtension.SceneGraph,'brs-sg.node.js']]);const timer=setTimeout(()=>brs.terminateApp(),25000);const result=await brs.executeApp(payload);clearTimeout(timer);assert.match(log,/TASK_PASSED/,log);assert.doesNotMatch(log,/TASK_FAILED|BRIGHTSCRIPT: ERROR|EXIT_BRIGHTSCRIPT_CRASH/,log);assert.ok(requests.length>=5);assert.equal(requests[0].searchParams.get('username'),'u/a');assert.equal(requests[0].searchParams.get('password'),'p?&');}
 finally{brs.unsubscribeHost('test');await brs.terminateApp();await new Promise(r=>server.close(r));}
});
