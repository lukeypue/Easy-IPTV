const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const brs=require('brs-node');
const sg=require('brs-node/bin/brs-sg.node.js');
brs.registerExtension(()=>new sg.BrightScriptExtension());
const root=path.resolve(__dirname,'..');
function files(){const map=new Map();for(const sub of ['source','components','images']){walk(path.join(root,sub));}map.set('manifest',new Blob([fs.readFileSync(path.join(root,'manifest'))]));return map;function walk(dir){for(const ent of fs.readdirSync(dir,{withFileTypes:true})){const p=path.join(dir,ent.name);if(ent.isDirectory())walk(p);else map.set(path.relative(root,p),new Blob([fs.readFileSync(p)]));}}}
async function run(body,hook=""){const map=files();if(hook){const key="components/RyzodScene.brs";map.set(key,new Blob([await map.get(key).text(),"\n",hook]));}for(const name of ['RyzodScene','RyzodKeyboard','RyzodGuide','RyzodActions','RyzodSchedule']){const key=`components/${name}.xml`;let xml=await map.get(key).text();xml=xml.replace('<interface>','<interface>\n<function name="onKeyEvent" />');if(name==='RyzodScene')xml=xml.replace('<interface>','<interface>\n<function name="beginLogin" /><function name="selectSection" /><function name="regressionHook" />');map.set(key,new Blob([xml]));}map.set('source/main.brs',new Blob([`sub Main()\nscreen=createObject("roSGScreen")\nscene=screen.createScene("RyzodScene")\nscreen.show()\n${body}\nprint "UI_PASSED"\nscreen.close()\nend sub\nsub expect(ok as boolean,message as string)\nif not ok then print "UI_FAILED: ";message\nend sub\n`]));let log='';brs.registerCallback((msg)=>{if(typeof msg==='string')log+=msg+'\n';});const payload=await brs.createPayloadFromFileMap(map,{developerId:'ryzod-tests',localIps:['127.0.0.1'],timeZone:'America/Denver',displayMode:'720p',locale:'en_US'});const result=await brs.executeFile(payload);assert.match(log,/UI_PASSED/,log);assert.doesNotMatch(log,/UI_FAILED|BRIGHTSCRIPT: ERROR|runtime error|Attempt to|not a function/i,log);return log;}
test('native startup and login render cleanly with a password field',async()=>run(`
expect(scene.findNode("title").text="Let's set up your playlist","initial login")
scene.callFunc("onKeyEvent","OK",true)
expect(scene.findNode("title").text="Sign in to your service","xtream")
scene.callFunc("onKeyEvent","OK",true)
expect(scene.findNode("keyboard").visible,"keyboard opens")
scene.findNode("keyboard").callFunc("onKeyEvent","back",true)
expect(not scene.findNode("keyboard").visible,"keyboard closes")
`));
test('native three-page keyboard wraps and commits URL punctuation',async()=>run(`
k=scene.findNode("keyboard")
k.value="https:"
k.opened=true
k.callFunc("onKeyEvent","options",true)
k.callFunc("onKeyEvent","options",true)
k.callFunc("onKeyEvent","OK",true)
expect(k.value="https:/","symbols")
k.callFunc("onKeyEvent","left",true)
k.callFunc("onKeyEvent","OK",true)
expect(k.value="https:/$","left wrap")
k.callFunc("onKeyEvent","back",true)
expect(k.value="https:","cancel restores")
`));
test('native horizontal actions wrap and Back cancels',async()=>run(`
a=scene.findNode("actions")
a.title="Delete?"
a.description="Confirm"
a.actions=[{id:"yes",title:"Yes"},{id:"close",title:"Close"}]
a.opened=true
a.callFunc("onKeyEvent","left",true)
a.callFunc("onKeyEvent","OK",true)
expect(a.selected="close","left wrap")
a.opened=true
a.callFunc("onKeyEvent","back",true)
expect(a.selected="close" and not a.visible,"cancel")
`));
test('native guide preserves selected row on data refresh and draws one duration block',async()=>run(`
g=scene.findNode("guide")
g.windowStart=1000
g.channels=[{id:"1",title:"One",kind:"live",url:"http://test/1.m3u8",format:"hls"},{id:"2",title:"Two",kind:"live",url:"http://test/2.m3u8",format:"hls"}]
g.epg={"1":[{start:1000,end:8200,title:"Two hours",desc:""}]}
g.selectedIndex=1
g.callFunc("onKeyEvent","OK",true)
expect(g.selected.channel.id="2","restore")
g.epg={"1":[{start:1000,end:8200,title:"Two hours",desc:""}]}
g.callFunc("onKeyEvent","OK",true)
expect(g.selected.channel.id="2","refresh keeps row")
expect(g.selected.program.gap,"blank clickable")
`));
test('native manual time dialog consumes arrows and Back',async()=>run(`
s=scene.findNode("schedule")
s.channelName="Test channel"
s.opened=true
s.callFunc("onKeyEvent","right",true)
s.callFunc("onKeyEvent","up",true)
expect(s.visible,"arrows stay in dialog")
s.callFunc("onKeyEvent","back",true)
expect(not s.visible and s.cancelled,"cancel")
`));
test('native empty catalogs still expose a category control and header navigation',async()=>run(`
scene.callFunc("selectSection",1)
expect(scene.findNode("content").getChildCount()>0,"empty screen usable")
scene.callFunc("onKeyEvent","up",true)
scene.callFunc("onKeyEvent","OK",true)
expect(scene.findNode("content").getChildCount()>3,"category choices")
`));

test('guide Up crosses six-row pages',async()=>run(`
g=scene.findNode("guide")
g.channels=[{id:"0",title:"0",kind:"live"},{id:"1",title:"1",kind:"live"},{id:"2",title:"2",kind:"live"},{id:"3",title:"3",kind:"live"},{id:"4",title:"4",kind:"live"},{id:"5",title:"5",kind:"live"},{id:"6",title:"6",kind:"live"}]
g.selectedIndex=6
g.callFunc("onKeyEvent","up",true)
g.callFunc("onKeyEvent","OK",true)
expect(g.selected<>invalid,"selected")
if g.selected<>invalid then expect(g.selected.index=5,"previous page row")
`));
test('live search, playlist switch and late episodes are isolated',async()=>run(`scene.callFunc("regressionHook")`, `
sub regressionHook()
 m.store.playlists=[{type:"xtream",host:"http://one",user:"u",pass:"p",name:"One"},{type:"xtream",host:"http://two",user:"u",pass:"p",name:"Two"}]
 m.store.active=0
 showItem({id:"1",kind:"live",title:"Search channel",url:"http://one/1.m3u8",format:"hls"})
 if not m.actions.visible then print "UI_FAILED: search actions"
 m.actions.visible=false
 m.previousChannel={id:"old",kind:"live",url:"http://one/old"}
 m.jobTask=createObject("roSGNode","RequestTask")
 activatePlaylist(1)
 if m.jobTask<>invalid then print "UI_FAILED: old job task retained"
 if m.previousChannel<>invalid then print "UI_FAILED: previous playlist retained"
 m.catalogId=999
 m.selectedSeries={title:"Late series"}
 selectSection(7)
 event={data:{id:999,generation:m.generation,kind:"episodes",ok:true,data:{items:[]}},getData:regressionData}
 onCatalogResponse(event)
 if m.view<>"items" or m.section<>"settings" then print "UI_FAILED: stale episodes"
end sub
function regressionData() as object
 return m.data
end function
`));
test('companion reservation gates playback and expiry stops provider video',async()=>run(`scene.callFunc("regressionHook")`, `
sub regressionHook()
 m.store.playlists=[{type:"xtream",host:"http://one",user:"u",pass:"p",name:"One"}]
 m.store.active=0
 m.store.settings.companion="http://companion"
 m.store.settings.token="token"
 item={id:"1",kind:"live",title:"One",url:"http://one/1.m3u8",format:"hls"}
 startPlayback(item,true)
 if m.playerItem<>invalid or m.pendingPlayback=invalid then print "UI_FAILED: playback before approval"
 onPlaybackLease({data:{id:m.leaseId,generation:m.generation,ok:true,data:{allowed:false}},getData:regressionData})
 if m.pendingPlayback<>invalid or m.playerItem<>invalid then print "UI_FAILED: denied playback"
 startPlayback(item,true)
 onPlaybackLease({data:{id:m.leaseId,generation:m.generation,ok:true,data:{allowed:true}},getData:regressionData})
 if m.playerItem=invalid then print "UI_FAILED: approved playback"
 m.leaseExpires=0
 onPlaybackTick()
 if m.playerItem<>invalid then print "UI_FAILED: expired playback"
 startPlayback(item,true)
 m.leaseStarted=NowSeconds()-8
 onPlaybackLease({data:{id:m.leaseId,generation:m.generation,ok:true,data:{allowed:true}},getData:regressionData})
 if m.playerItem<>invalid then print "UI_FAILED: delayed lease approval"
 m.actionItem=item
 now=NowSeconds()
 m.actionProgram={start:now+100,end:now+3000,programStart:now+100,programEnd:now+20000,gap:false}
 prepareRecording(false)
 if m.pendingJob.duration<>19900 then print "UI_FAILED: truncated recording"
end sub
function regressionData() as object
 return m.data
end function
`));
test('choosing a stopped item again starts a new Video content request',async()=>run(`scene.callFunc("regressionHook")`, `
sub regressionHook()
 m.store.playlists=[{type:"xtream",host:"http://one",user:"u",pass:"p",name:"One"}]
 m.store.active=0
 item={id:"1",kind:"live",title:"One",url:"http://one/1.m3u8",format:"hls"}
 m.store.settings.companion=""
 m.store.settings.token=""
 m.playerItem=item
 old=createObject("roSGNode","ContentNode")
 old.url="http://old"
 m.video.content=old
 m.video.control="stop"
 startPlayback(item,true)
 if m.video.content.url<>item.url then print "UI_FAILED: stopped item did not restart"
end sub
`));
test('native track selection uses Roku subtitle identifiers and enables captions',async()=>run(`scene.callFunc("regressionHook")`, `
sub regressionHook()
 original=m.video
 m.video={availableAudioTracks:[{Track:"audio-en",Name:"English"}],availableSubtitleTracks:[{TrackName:"captions-en",Description:"English captions"}],globalCaptionMode:"Off",subtitleTrack:"",suppressCaptions:true}
 showTracks()
 if m.trackMap.subtitle2.track<>"captions-en" then print "UI_FAILED: caption ID"
 onActionSelected({getData:captionSelection})
 if m.video.subtitleTrack<>"captions-en" or m.video.globalCaptionMode<>"On" then print "UI_FAILED: captions not enabled"
 m.video=original
end sub
function captionSelection() as string
 return "subtitle2"
end function
`));
test('large action lists keep a readable four-button viewport and wrap',async()=>run(`
a=scene.findNode("actions")
a.actions=[{id:"0",title:"0"},{id:"1",title:"1"},{id:"2",title:"2"},{id:"3",title:"3"},{id:"4",title:"4"},{id:"5",title:"5"},{id:"close",title:"Close"}]
a.opened=true
expect(a.findNode("body").getChildCount()=9,"four visible buttons")
a.callFunc("onKeyEvent","left",true)
a.callFunc("onKeyEvent","OK",true)
expect(a.selected="close","last page wrap")
`));
test('background catalog completion preserves fullscreen focus; removing last playlist cancels work',async()=>run(`scene.callFunc("regressionHook")`, `
sub regressionHook()
 m.store.playlists=[{type:"xtream",host:"http://one",user:"u",pass:"p",name:"One"}]
 m.store.active=0
 m.store.settings.companion=""
 m.store.settings.lastTune=false
 m.mode="home"
 m.section="live"
 m.focus="content"
 m.catalogId=999
 m.fullscreen=true
 m.shell.visible=false
 onCatalogResponse({data:{id:999,generation:m.generation,kind:"catalog",ok:true,data:{items:[],categories:[],mediaKind:"live",category:"all"}},getData:regressionData})
 if m.shell.visible then print "UI_FAILED: background update changed fullscreen"
 m.fullscreen=false
 m.removeIndex=0
 m.jobTask=createObject("roSGNode","RequestTask")
 m.catalogTask=createObject("roSGNode","RequestTask")
 m.epgTask=createObject("roSGNode","RequestTask")
 onActionSelected({getData:removeSelection})
 if m.jobTask<>invalid or m.catalogTask<>invalid or m.epgTask<>invalid then print "UI_FAILED: work retained after removal"
end sub
function regressionData() as object
 return m.data
end function
function removeSelection() as string
 return "confirmRemovePlaylist"
end function
`));
