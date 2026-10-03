const {test} = require('node:test');
const assert = require('node:assert/strict');
const {spawnSync} = require('node:child_process');
const {mkdtempSync,writeFileSync,readdirSync,rmSync} = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const root = path.resolve(__dirname,'..');
function check(name,body){test(name,()=>{const dir=mkdtempSync(path.join(os.tmpdir(),'ryzod-brs-'));try {const file=path.join(dir,'test.brs');writeFileSync(file,'sub main()\n'+body+'\nprint "CHECK_PASSED"\nend sub\nsub expect(ok as boolean, message as string)\nif not ok then\nprint "ASSERTION_FAILED: ";message\nstop\nend if\nend sub\n');const core=path.join(root,'source/core');let sources=[];try {sources=readdirSync(core).filter(x=>x.endsWith('.brs')).map(x=>path.join(core,x));}catch{}const out=spawnSync(process.execPath,[path.join(root,'node_modules/@rokucommunity/brs/bin/cli.js'),'-r',dir,...sources,file],{encoding:'utf8',timeout:20000,env:{...process.env,TZ:'America/Denver'}});assert.equal(out.status,0,out.stdout+out.stderr);assert.match(out.stdout,/CHECK_PASSED/,out.stdout+out.stderr);assert.doesNotMatch(out.stdout,/ASSERTION_FAILED/);}finally{rmSync(dir,{recursive:true,force:true});}});}
check('provider addresses normalize without corrupting ports or paths',`
expect(NormalizeHost(" example.com:8080/player_api.php?x=1 ") = "http://example.com:8080", "normalize")
expect(NormalizeHost("https://example.com/base/get.php?x=1") = "https://example.com/base", "path")
expect(NormalizeHost("file:///etc/passwd") = "", "scheme")
`);
check('API and stream credentials are UTF8 encoded as path segments',`
p={host:"http://test:8080",user:"a/b +",pass:"p?&é",type:"xtream"}
u=ApiUrl(p,"get_short_epg",{stream_id:"12",limit:24})
expect(instr(1,u,"username=a%2Fb%20%2B")>0,"username")
expect(instr(1,u,"password=p%3F%26%C3%A9")>0,"password")
expect(StreamUrl(p,"live","12","m3u8")="http://test:8080/live/a%2Fb%20%2B/p%3F%26%C3%A9/12.m3u8","path")
`);
check('invalid or error objects are rejected instead of a fake empty catalog',`
expect(CatalogRows({error:"Denied"}).ok=false,"error")
expect(CatalogRows(invalid).ok=false,"invalid")
expect(CatalogRows({data:[]}).ok=true,"empty wrapped")
expect(CatalogRows({streams:[{stream_id:1}]}).rows.count()=1,"wrapped")
`);
check('live catalog is deduplicated with HLS default and safe missing values',`
p={host:"http://test",user:"u",pass:"p",type:"xtream"}
r=NormalizeCatalog([{stream_id:1,name:"News",category_id:2},invalid,{stream_id:1,name:"duplicate"},{name:"bad"},{stream_id:2}],"live",p,"hls")
expect(r.count()=2,"count")
expect(r[0].url="http://test/live/u/p/1.m3u8","hls")
expect(r[1].title="Channel","fallback")
`);
check('M3U parsing keeps commas in titles, quoted groups, URLs and movie types',`
raw=chr(65279)+"#EXTM3U x-tvg-url="+chr(34)+"https://test/epg.xml"+chr(34)+chr(10)+"#EXTINF:-1 tvg-id="+chr(34)+"x"+chr(34)+" group-title="+chr(34)+"US, News"+chr(34)+",News, World"+chr(10)+"#EXTVLCOPT:http-user-agent=Custom"+chr(10)+"https://test/live.m3u8"+chr(10)+"#EXTINF:-1 group-title="+chr(34)+"Movies"+chr(34)+",Film"+chr(10)+"https://test/movie.mp4"
r=ParseM3u(raw)
expect(r.ok,"parse")
expect(r.items.count()=2,"items")
expect(r.items[0].title="News, World","commas")
expect(r.items[0].category="US, News","group")
expect(r.items[0].agent="Custom","agent")
expect(r.items[1].kind="movies","movie")
expect(r.epgUrl="https://test/epg.xml","epg")
expect(ParseM3u("<html>login</html>").ok=false,"html")
`);
check('series episode variants use numeric season and episode order',`
p={host:"http://test",user:"u",pass:"p"}
r=ParseEpisodes({episodes:{"10":[{id:102,title:"Ten",episode_num:2}],"2":[{id:21,title:"Two B",episode_num:10},{id:22,title:"Two A",episode_num:2}]}},p)
expect(r.count()=3,"count")
expect(r[0].id="22","sort")
expect(r[2].season=10,"season")
expect(r[0].url="http://test/series/u/p/22.mp4","url")
`);
check('EPG preserves actual duration with clipping, selectable gaps and overlaps',`
r=BuildGuideCells([{start:100,end:400,title:"Three hours",desc:""},{start:350,end:500,title:"Overlap",desc:""}],0,600)
expect(r.count()=4,"cells")
expect(r[0].gap,"gap")
expect(r[1].start=100 and r[1].end=400,"duration")
expect(r[2].start=400 and r[2].end=500,"overlap")
expect(r[3].end=600,"tail")
expect(GuideCellIndex(r,480)=2,"index")
expect(BuildGuideCells([],100,300)[0].gap,"blank")
`);
check('EPG validates timestamps and decodes readable base64 title safely',`
r=ParseEpg({epg_listings:[{start_timestamp:"100",stop_timestamp:"7300",title:"TmV3cw==",description:"Plain text"},{start_timestamp:"bad",title:"No"}]})
expect(r.count()=1,"rows")
expect(r[0].title="News","decode")
expect(r[0].desc="Plain text","plain")
expect(r[0].end-r[0].start=7200,"seconds")
expect(ParseXmltvTime("20261003120000 +0200")=1791021600,"offset")
`);
check('favorites and resume keys isolate playlists and media kinds',`
p1={type:"xtream",host:"http://one",user:"u",pass:"p"}
p2={type:"xtream",host:"http://two",user:"u",pass:"p"}
expect(ItemKey(p1,{kind:"live",id:"1"})<>ItemKey(p2,{kind:"live",id:"1"}),"playlist")
expect(ItemKey(p1,{kind:"live",id:"1"})<>ItemKey(p1,{kind:"movies",id:"1"}),"kind")
expect(ResumePosition(290,300)=0,"complete")
expect(ResumePosition(120,300)=120,"resume")
expect(ResumePosition(-1,300)=0,"negative")
`);
check('manual recording only permits future seven-day and five-minute slots',`
expect(ValidateSchedule(1000,1000,60).ok=false,"past")
expect(ValidateSchedule(1000,1500,60).ok=true,"future")
expect(ValidateSchedule(1000,605801,60).ok=false,"week")
expect(ValidateSchedule(1000,1501,60).ok=false,"step")
expect(ValidateSchedule(1000,1500,0).ok=false,"duration")
`);
check('keyboard wraps in both directions and includes URL symbols on three pages',`
expect(KeyboardPages().count()=3,"pages")
expect(KeyboardMove(9,"right",10,50)=0,"right wrap")
expect(KeyboardMove(10,"left",10,50)=19,"left wrap")
expect(KeyboardMove(2,"up",10,50)=42,"up wrap")
s=""
for each page in KeyboardPages()
for each k in page
s=s+k
end for
end for
expect(instr(1,s,"/")>0 and instr(1,s,":")>0 and instr(1,s,"&")>0,"symbols")
`);
check('action rows put playback first and Close last with conditional job actions',`
r=ItemActions("live","",false)
expect(r[0].id="watch" and r[r.count()-1].id="close","live")
r=ItemActions("movies","",true)
expect(r[0].id="play" and r[1].id="download","movie")
r=ItemActions("downloads","paused",false)
expect(r[0].id="play" and r[1].id="resume" and r[r.count()-1].id="close","download")
`);
check('catalog filters perform literal slash searches',`
r=FilterItems([{title:"20/20",category:"News",kind:"live",id:"1"},{title:"2020",category:"News",kind:"live",id:"2"}],"News","20/20")
expect(r.count()=1 and r[0].id="1","literal")
expect(FilterItems([],"all","").count()=0,"empty")
`);
check('stale response and retry helpers protect a newer playlist',`
expect(ResponseMatches({generation:2,id:7},2,7),"valid")
expect(not ResponseMatches({generation:1,id:7},2,7),"generation")
expect(not ResponseMatches({generation:2,id:6},2,7),"id")
expect(WrapIndex(-1,8)=7 and WrapIndex(8,8)=0,"nav")
`);
check('manual schedule uses civil dates across a DST change',`
now = ParseXmltvTime("20261031120000 -0600")
expect(ScheduleLocalTime(2026,11,1,12,0,now)=ParseXmltvTime("20261101120000 -0700"),"DST target offset")
`);
check('M3U extensionless live URLs and stream types are handled explicitly',`
raw="#EXTM3U"+chr(10)+"#EXTINF:-1,Live"+chr(10)+"http://test/stream?token=a"+chr(10)+"#EXTINF:-1 type="+chr(34)+"vod"+chr(34)+",Film"+chr(10)+"http://test/vod/1"
r=ParseM3u(raw)
expect(r.items[0].kind="live","extensionless live")
expect(r.items[1].kind="movies","vod attribute")
`);

check('guide display clipping retains the complete program recording bounds',`
r=BuildGuideCells([{start:100,end:10000,title:"Long movie"}],500,2000)
expect(r[0].start=500 and r[0].end=2000,"display bounds")
expect(r[0].programStart=100 and r[0].programEnd=10000,"recording bounds")
`);
check('native live rewind remains inside the available pause buffer',`
expect(SeekTarget("live",0,0,100,300,200,-300)=100,"buffer start")
expect(SeekTarget("live",0,0,100,300,200,300)=300,"buffer end")
expect(SeekTarget("live",0,0,0,0,0,-30)=invalid,"no buffer")
expect(SeekTarget("movies",50,100,0,0,0,100)=99,"VOD end")
`);
