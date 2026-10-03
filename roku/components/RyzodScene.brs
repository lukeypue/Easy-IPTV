' Native RYZOD application. Network I/O lives exclusively in RequestTask.
sub init()
    m.top.backgroundColor = "0x07111FFF"
    for each id in ["shell","rail","content","title","subtitle","clock","status","video","guide","keyboard","actions","mini","schedule","clockTimer","epgTimer","miniTimer","playbackTimer","jobsTimer"]
        m[id] = m.top.findNode(id)
    end for
    m.menu = ["Live TV","Movies","Series","Search","Downloads","Recordings","Playlists","Settings"]
    m.sections = ["live","movies","series","search","downloads","recordings","playlists","settings"]
    m.generation = 0
    m.nextRequest = 0
    m.menuIndex = 0
    m.section = "live"
    m.focus = "rail"
    m.view = "items"
    m.row = 0
    m.rows = []
    m.items = {live:[],movies:[],series:[]}
    m.categories = {live:[],movies:[],series:[]}
    m.loaded = {}
    m.category = {live:"all",movies:"all",series:"all"}
    m.epg = {}
    m.epgLoaded = {}
    m.epgQueue = []
    m.epgTask = invalid
    m.catalogTask = invalid
    m.jobTask = invalid
    m.playerItem = invalid
    m.leaseTask = invalid
    m.releaseTask = invalid
    m.playbackSession = createObject("roDeviceInfo").getRandomUUID()
    m.leaseId = -1
    m.pendingPlayback = invalid
    m.leaseExpires = 0
    m.playbackClient = "roku-"+createObject("roDeviceInfo").getChannelClientId()
    m.previousChannel = invalid
    m.fullscreen = false
    m.miniIndex = 0
    m.miniActions = []
    m.query = ""
    m.searchScope = "live"
    m.jobs = []
    m.jobScope = ""
    m.returnView = invalid
    m.confirmAction = ""
    m.bufferTicks = 0
    m.playTicks = 0
    m.registry = createObject("roRegistrySection","ryzod")
    loadStore()
    m.keyboard.observeField("done","onKeyboardDone")
    m.keyboard.observeField("cancelled","onKeyboardCancel")
    m.actions.observeField("selected","onActionSelected")
    m.guide.observeField("selected","onGuideSelected")
    m.guide.observeField("command","onGuideCommand")
    m.guide.observeField("selectionChanged","onGuideChanged")
    m.schedule.observeField("selected","onScheduleSelected")
    m.schedule.observeField("cancelled","restoreFocus")
    m.video.observeField("state","onVideoState")
    m.clockTimer.observeField("fire","updateClock")
    m.epgTimer.observeField("fire","queueVisibleEpg")
    m.miniTimer.observeField("fire","hideMini")
    m.playbackTimer.observeField("fire","onPlaybackTick")
    m.jobsTimer.observeField("fire","pollJobs")
    m.clockTimer.control = "start"
    m.playbackTimer.control = "start"
    m.jobsTimer.control = "start"
    updateClock()
    if m.store.playlists.count() = 0
        showLoginTypes()
    else
        activatePlaylist(m.store.active)
    end if
end sub

sub loadStore()
    raw = m.registry.read("state")
    store = invalid
    if raw <> "" then store = parseJson(raw)
    if not IsMap(store) then store = {}
    if not IsList(store.playlists) then store.playlists = []
    if not IsMap(store.favorites) then store.favorites = {}
    if not IsMap(store.resume) then store.resume = {}
    if not IsMap(store.lastLive) then store.lastLive = {}
    if not IsMap(store.settings) then store.settings = {}
    if store.active = invalid then store.active = 0
    if store.active < 0 or store.active >= store.playlists.count() then store.active = 0
    store.settings.companion = FieldText(store.settings,"companion")
    store.settings.token = FieldText(store.settings,"token")
    store.settings.liveFormat = FieldText(store.settings,"liveFormat","hls")
    if store.settings.lastTune = invalid then store.settings.lastTune = false
    if store.settings.streams = invalid then store.settings.streams = 1
    while store.playlists.count() > 3
        store.playlists.pop()
    end while
    m.store = store
    m.lastSaved = formatJson(store)
end sub

function saveStore() as boolean
    raw = formatJson(m.store)
    if len(raw) > 28000
        m.store = parseJson(m.lastSaved)
        m.status.text = "Saved settings are full. Remove some favorites or playlists and try again."
        return false
    end if
    if not m.registry.write("state",raw)
        m.store = parseJson(m.lastSaved)
        m.status.text = "Couldn't save settings on this Roku."
        return false
    end if
    m.registry.flush()
    m.lastSaved = raw
    return true
end function

sub updateClock()
    m.clock.text = ClockText(NowSeconds())
    if m.fullscreen and m.mini.visible then drawMini()
end sub

function activePlaylist() as object
    if m.store.playlists.count() = 0 then return {}
    return m.store.playlists[m.store.active]
end function

sub activatePlaylist(index as integer)
    stopPlayback()
    cancelCatalog()
    if m.epgTask <> invalid then m.epgTask.control = "stop"
    m.epgTask = invalid
    cancelJobs()
    m.previousChannel = invalid
    m.actionProgram = invalid
    m.actionItem = invalid
    m.selectedSeries = invalid
    m.generation = m.generation+1
    m.store.active = index
    saveStore()
    m.items = {live:[],movies:[],series:[]}
    m.categories = {live:[],movies:[],series:[]}
    m.loaded = {}
    m.epg = {}
    m.epgLoaded = {}
    m.epgQueue = []
    m.category = {live:"all",movies:"all",series:"all"}
    m.epgUrl = ""
    m.mode = "home"
    m.menuIndex = 0
    m.section = "live"
    m.focus = "rail"
    m.view = "items"
    m.row = 0
    m.guide.windowStart = int(NowSeconds()/1800)*1800
    m.guide.epg = m.epg
    renderHome()
    requestCatalog("live","all")
end sub

sub renderRail()
    ClearGroup(m.rail)
    for i = 0 to m.menu.count()-1
        UiButton(m.rail,m.menu[i],0,i*62,198,52,m.focus="rail" and m.menuIndex=i)
    end for
end sub

sub renderHome()
    if m.fullscreen then return
    m.mode = "home"
    if not m.actions.visible and not m.keyboard.visible and not m.schedule.visible then m.top.setFocus(true)
    m.shell.visible = true
    m.title.text = m.menu[m.menuIndex]
    m.subtitle.text = FieldText(activePlaylist(),"name","My playlist")
    m.content.visible = true
    m.guide.visible = false
    renderRail()
    ClearGroup(m.content)
    if m.section = "live"
        m.content.visible = false
        m.guide.visible = true
        channels = FilterItems(m.items.live,m.category.live,"")
        if m.category.live = "favorites" then channels = favoriteItems(m.items.live)
        m.guide.channels = channels
        m.guide.epg = m.epg
        m.subtitle.text = "Provider guide · Refresh can take a minute or longer."
        if m.focus = "content"
            m.guide.setFocus(true)
            m.guide.callFunc("drawGuide")
        end if
        queueVisibleEpg()
    else if m.section = "movies" or m.section = "series"
        kind = m.section
        if m.view = "categories"
            m.rows = categoryRows(kind)
        else if m.view = "episodes" or m.view = "seasons"
            ' Current series rows are retained while returning from playback.
        else
            m.view = "items"
            m.rows = FilterItems(m.items[kind],m.category[kind],"")
            if m.category[kind] = "favorites" then m.rows = favoriteItems(m.items[kind])
        end if
        drawList()
    else if m.section = "search"
        m.rows = [{title:"Search: "+m.query,type:"query"},{title:"Search in: "+scopeTitle(m.searchScope),type:"scope"}]
        results = FilterItems(m.items[m.searchScope],"all",m.query)
        if m.query <> ""
            for each item in results
                m.rows.push(item)
            end for
        end if
        drawList()
        m.subtitle.text = "Search Live TV, Movies or Series. Slash and punctuation searches are supported."
    else if m.section = "playlists"
        m.rows = []
        for i = 0 to m.store.playlists.count()-1
            marker = ""
            if i = m.store.active then marker = " · active"
            m.rows.push({title:m.store.playlists[i].name+marker,type:"playlist",index:i})
        end for
        if m.store.playlists.count() < 3 then m.rows.push({title:"Add playlist",type:"addPlaylist"})
        drawList()
        m.subtitle.text = "Up to three playlists. Press * on a playlist to remove it."
    else if m.section = "settings"
        m.rows = [
            {title:"Companion address: "+m.store.settings.companion,type:"setting",key:"companion"},
            {title:"Companion pairing token: "+maskedToken(),type:"setting",key:"token"},
            {title:"Live format: "+ucase(m.store.settings.liveFormat),type:"setting",key:"liveFormat"},
            {title:"Auto-play last channel: "+boolLabel(m.store.settings.lastTune),type:"setting",key:"lastTune"},
            {title:"Provider streams: "+m.store.settings.streams.toStr(),type:"setting",key:"streams"},
            {title:"Test companion connection",type:"setting",key:"test"},
            {title:"Reload current playlist",type:"setting",key:"reload"},
            {title:"About RYZOD / Roku compatibility",type:"setting",key:"about"}
        ]
        drawList()
        m.subtitle.text = "RYZOD Roku 0.1.0 · based on Android 4.70"
    else if m.section = "recordings" or m.section = "downloads"
        m.rows = []
        for each job in m.jobs
            if job.kind = m.section then m.rows.push(job)
        end for
        drawList()
        if not hasCompanion()
            label = UiLabel(m.content,"Connect the optional RYZOD companion in Settings to save recordings and downloads on your computer or NAS.",20,90,860,180,24,"0xBFEFFFFF")
            label.wrap = true
            label.numLines = 4
        end if
    end if
    if not m.fullscreen then setPreview()
end sub

function scopeTitle(kind as string) as string
    if kind = "live" then return "Live TV"
    if kind = "movies" then return "Movies"
    return "Series"
end function

function boolLabel(value as boolean) as string
    if value then return "ON"
    return "OFF"
end function

function maskedToken() as string
    if m.store.settings.token = "" then return "not set"
    return "saved"
end function

sub drawList()
    ClearGroup(m.content)
    count = m.rows.count()
    if m.row < -1 or m.row >= count then m.row = 0
    if m.section = "movies" or m.section = "series"
        text = "Categories · "+m.category[m.section]
        if m.view = "seasons" then text = "Seasons · Back to series"
        if m.view = "episodes" then text = "Episodes · Back to seasons"
        UiButton(m.content,text,0,0,920,44,m.row=-1 and m.focus="content")
    end if
    if count = 0
        UiLabel(m.content,"No items here yet. Choose a category or refresh.",14,120,900,70,24,"0xBFEFFFFF")
        return
    end if
    if isCatalogGrid()
        drawCatalogGrid()
        return
    end if
    first = int(m.row/7)*7
    top = 54
    for i = first to first+6
        if i < count
            item = m.rows[i]
            text = item.title
            if FieldText(item,"kind") = "recordings" or FieldText(item,"kind") = "downloads"
                text = text+"  ·  "+FieldText(item,"state")
                if item.bytes <> invalid then text = text+"  ·  "+int(item.bytes/1048576).toStr()+" MB"
            else if FieldText(item,"kind") <> ""
                if isFavorite(item) then text = "★ "+text
            end if
            UiButton(m.content,text,0,top+(i-first)*62,920,54,m.focus="content" and m.row=i)
            ' Only visible items get poster nodes, keeping the tree bounded.
            icon = FieldText(item,"icon")
            if icon <> "" then UiPoster(m.content,icon,14,top+(i-first)*62+5,44,44)
        end if
    end for
    UiLabel(m.content,(m.row+1).toStr()+" / "+count.toStr()+"  ·  Back returns to categories/menu",8,484,900,26,17,"0xBFEFFFFF")
end sub

function categoryRows(kind as string) as object
    result = [{title:"All",type:"category",id:"all"},{title:"Favorites",type:"category",id:"favorites"}]
    for each category in m.categories[kind]
        result.push({title:category.title,type:"category",id:category.id})
    end for
    return result
end function

function isFavorite(item as object) as boolean
    key = ItemKey(activePlaylist(),item)
    return m.store.favorites.doesExist(key)
end function

function favoriteItems(items as object) as object
    result = []
    for each item in items
        if isFavorite(item) then result.push(item)
    end for
    return result
end function

sub toggleFavorite(item as object)
    key = ItemKey(activePlaylist(),item)
    if m.store.favorites.doesExist(key)
        m.store.favorites.delete(key)
    else
        if m.store.favorites.count() >= 150
            m.status.text = "Remove a favorite before adding another (150 saved favorites)."
            return
        end if
        m.store.favorites[key] = true
    end if
    saveStore()
end sub

sub cancelCatalog()
    if m.catalogTask <> invalid then m.catalogTask.control = "stop"
    m.catalogTask = invalid
    m.catalogId = -1
end sub

function newRequest(kind as string) as object
    m.nextRequest = m.nextRequest+1
    return {id:m.nextRequest,generation:m.generation,kind:kind,playlist:activePlaylist(),liveFormat:m.store.settings.liveFormat}
end function

sub startCatalogTask(request as object)
    cancelCatalog()
    m.catalogId = request.id
    m.catalogTask = createObject("roSGNode","RequestTask")
    m.catalogTask.observeField("response","onCatalogResponse")
    m.catalogTask.request = request
    m.catalogTask.control = "run"
    m.status.text = "Loading from your provider… Back remains available."
end sub

sub requestCatalog(kind as string, category as string)
    request = newRequest("catalog")
    request.mediaKind = kind
    request.category = category
    startCatalogTask(request)
end sub

sub onCatalogResponse(event as object)
    response = event.getData()
    if not ResponseMatches(response,m.generation,m.catalogId) then return
    m.catalogTask = invalid
    if not response.ok
        m.status.text = response.error
        if m.mode = "login" then renderLogin()
        return
    end if
    if response.kind = "connect"
        if m.store.playlists.count() >= 3 then return
        m.store.playlists.push(m.draft)
        if saveStore() then activatePlaylist(m.store.playlists.count()-1)
        return
    end if
    if response.kind = "episodes"
        m.episodes = response.data.items
        showSeasons()
        m.status.text = ""
        return
    end if
    data = response.data
    if activePlaylist().type = "m3u"
        m.items.live = []
        m.items.movies = []
        m.categories.live = []
        m.categories.movies = []
        seen = {live:{},movies:{}}
        for each item in data.items
            m.items[item.kind].push(item)
            if not seen[item.kind].doesExist(item.category)
                seen[item.kind][item.category] = true
                m.categories[item.kind].push({id:item.category,title:item.category})
            end if
        end for
        m.epgUrl = data.epgUrl
        m.loaded.live = "all"
        m.loaded.movies = "all"
        m.loaded.series = "all"
    else
        kind = data.mediaKind
        m.items[kind] = data.items
        m.categories[kind] = data.categories
        m.loaded[kind] = data.category
    end if
    m.status.text = ""
    if m.mode = "home" then renderHome()
    if m.store.settings.lastTune and m.playerItem = invalid and m.section = "live"
        scope = PlaylistKey(activePlaylist())
        if m.store.lastLive.doesExist(scope)
            for each item in m.items.live
                if item.id = m.store.lastLive[scope]
                    startPlayback(item,false)
                    exit for
                end if
            end for
        end if
    end if
end sub

sub selectSection(index as integer)
    cancelCatalog()
    m.menuIndex = index
    m.section = m.sections[index]
    m.focus = "content"
    m.view = "items"
    m.row = 0
    m.top.setFocus(true)
    renderHome()
    if m.section = "live" or m.section = "movies" or m.section = "series"
        if not m.loaded.doesExist(m.section) then requestCatalog(m.section,"all")
    else if m.section = "recordings" or m.section = "downloads"
        requestJobs()
    end if
end sub

sub showLoginTypes()
    m.mode = "loginType"
    m.focus = "content"
    m.row = 0
    m.guide.visible = false
    m.content.visible = true
    m.shell.visible = true
    m.title.text = "Let's set up your playlist"
    m.subtitle.text = "Choose how your provider gave you access."
    ClearGroup(m.rail)
    UiLabel(m.rail,"TV made simple",0,0,190,70,20,"0x49E8FFFF")
    m.rows = [{title:"Username & password (Xtream)",type:"xtream"},{title:"Playlist link (M3U)",type:"m3u"}]
    drawList()
    m.status.text = "RYZOD is a player. Use your own authorized provider or playlist."
    m.top.setFocus(true)
end sub

sub beginLogin(kind as string)
    m.mode = "login"
    m.draft = {name:"My playlist",type:kind,host:"",user:"",pass:"",url:""}
    m.row = 0
    renderLogin()
end sub

sub renderLogin()
    m.title.text = "Sign in to your service"
    m.subtitle.text = "Your login is saved on this Roku."
    m.rows = [{title:"Playlist name: "+m.draft.name,type:"loginField",key:"name"}]
    if m.draft.type = "xtream"
        m.rows.push({title:"Server address: "+m.draft.host,type:"loginField",key:"host"})
        m.rows.push({title:"Username: "+m.draft.user,type:"loginField",key:"user"})
        m.rows.push({title:"Password: "+string(len(m.draft.pass),"*"),type:"loginField",key:"pass"})
    else
        m.rows.push({title:"Playlist link: "+m.draft.url,type:"loginField",key:"url"})
    end if
    m.rows.push({title:"Connect",type:"connect"})
    m.rows.push({title:"Different login type",type:"loginBack"})
    drawList()
end sub

sub connectDraft()
    if m.draft.type = "xtream"
        m.draft.host = NormalizeHost(m.draft.host)
        if m.draft.host = "" or m.draft.user = "" or m.draft.pass = ""
            m.status.text = "Fill in the server address, username and password."
            return
        end if
    else
        if not IsWebUrl(m.draft.url)
            m.status.text = "Enter a complete http:// or https:// playlist link."
            return
        end if
    end if
    request = newRequest("connect")
    request.playlist = m.draft
    startCatalogTask(request)
end sub

sub editText(context as string, field as string, title as string, value as string, password = false as boolean)
    m.editContext = context
    m.editField = field
    m.keyboard.title = title
    m.keyboard.value = value
    m.keyboard.password = password
    m.keyboard.opened = true
end sub

sub onKeyboardDone()
    value = m.keyboard.value
    if m.editContext = "login"
        m.draft[m.editField] = value.trim()
        renderLogin()
    else if m.editContext = "setting"
        if m.editField = "companion"
            value = NormalizeHost(value)
            if value <> "" and not IsWebUrl(value)
                m.status.text = "Enter a valid companion address."
                restoreFocus()
                return
            end if
        end if
        stopPlayback()
        cancelJobs()
        m.store.settings[m.editField] = value.trim()
        saveStore()
        renderHome()
    else if m.editContext = "search"
        m.query = value
        renderHome()
        if not m.loaded.doesExist(m.searchScope)
            requestCatalog(m.searchScope,"all")
        else if m.loaded[m.searchScope] <> "all"
            requestCatalog(m.searchScope,"all")
        end if
    end if
    restoreFocus()
end sub

sub onKeyboardCancel()
    restoreFocus()
end sub

sub restoreFocus()
    if m.keyboard.visible or m.actions.visible or m.schedule.visible then return
    if m.fullscreen
        m.top.setFocus(true)
    else if m.mode = "home" and m.section = "live" and m.focus = "content"
        m.guide.setFocus(true)
        m.guide.callFunc("drawGuide")
    else
        m.top.setFocus(true)
        if m.mode = "home" then renderRail()
    end if
end sub

sub openActions(title as string, description as string, actions as object, context as string)
    m.actionContext = context
    m.actions.title = title
    m.actions.description = description
    m.actions.actions = actions
    m.actions.opened = true
end sub

sub showItem(item as object, program = invalid as dynamic)
    m.actionItem = item
    kind = item.kind
    if kind = "series"
        m.selectedSeries = item
        m.seriesReturnRow = m.row
        openActions(item.title,FieldText(item,"desc"),[{id:"playSeries",title:"Play"},{id:"downloadSeries",title:"Download"},{id:"favorite",title:"Favorite / unfavorite"},{id:"close",title:"Close"}],"series")
        return
    end if
    description = FieldText(item,"desc")
    if kind = "live"
        if IsMap(program)
            m.actionProgram = program
        else
            m.actionProgram = {title:"Live TV",start:NowSeconds(),end:NowSeconds()+3600,gap:true}
            if m.epg.doesExist(item.id)
                for each program in m.epg[item.id]
                    if program.start <= NowSeconds() and program.end > NowSeconds() then m.actionProgram = program
                end for
            end if
        end if
        description = m.actionProgram.title+"  "+ClockText(m.actionProgram.start)+"–"+ClockText(m.actionProgram.end)+". You can record this channel by time, up to one week ahead. "+FieldText(m.actionProgram,"desc")
    end if
    openActions(item.title,description,ItemActions(kind,FieldText(item,"state"),isFavorite(item)),"item")
end sub

sub showSeasons()
    seasons = {}
    m.rows = []
    for each episode in m.episodes
        key = episode.season.toStr()
        if not seasons.doesExist(key)
            seasons[key] = true
            m.rows.push({title:"Season "+key,type:"season",season:episode.season})
        end if
    end for
    m.view = "seasons"
    m.row = 0
    m.title.text = m.selectedSeries.title
    drawList()
    restoreFocus()
end sub

sub showEpisodes(season as integer)
    m.seasonReturnRow = m.row
    m.selectedSeason = season
    m.rows = []
    for each episode in m.episodes
        if episode.season = season
            row = parseJson(formatJson(episode))
            row.title = "S"+episode.season.toStr()+" E"+episode.episode.toStr()+" · "+episode.title
            m.rows.push(row)
        end if
    end for
    m.view = "episodes"
    m.row = 0
    drawList()
end sub

sub onGuideSelected(event as object)
    value = event.getData()
    m.guide.selectedIndex = value.index
    m.actionProgram = value.program
    m.actionItem = value.channel
    if value.watch = true
        startPlayback(value.channel,true)
    else
        showItem(value.channel,value.program)
    end if
end sub

sub onGuideChanged()
    m.epgTimer.control = "stop"
    m.epgTimer.control = "start"
end sub

sub onGuideCommand(event as object)
    command = event.getData()
    if command = "rail"
        m.focus = "rail"
        m.top.setFocus(true)
        renderRail()
        m.guide.callFunc("drawGuide")
    else if command = "categories"
        m.rows = categoryRows("live")
        m.view = "categories"
        m.row = 0
        m.guide.visible = false
        m.content.visible = true
        drawList()
        m.top.setFocus(true)
    else if command = "refresh"
        m.epgLoaded = {}
        m.epgQueue = []
        m.status.text = "Updating guide from your provider. This may take one minute or longer."
        queueVisibleEpg()
    else if command = "preview"
        if m.playerItem <> invalid
            if m.playerItem.kind = "live" then startPlayback(m.playerItem,true)
        end if
    end if
end sub

sub queueVisibleEpg()
    if m.mode <> "home" or m.section <> "live" then return
    visible = m.guide.callFunc("visibleChannels")
    if not IsList(visible) then return
    m.epgQueue = []
    ' Focused row first; remaining visible rows follow without blocking navigation.
    focused = m.guide.selectionChanged
    if focused >= 0 and focused < m.guide.channels.count()
        channel = m.guide.channels[focused]
        if not m.epgLoaded.doesExist(channel.id) then m.epgQueue.push(channel)
    end if
    for each channel in visible
        if not m.epgLoaded.doesExist(channel.id) then m.epgQueue.push(channel)
    end for
    nextEpg()
end sub

sub nextEpg()
    if m.epgTask <> invalid then return
    while m.epgQueue.count() > 0
        channel = m.epgQueue.shift()
        if not m.epgLoaded.doesExist(channel.id)
            request = newRequest("epg")
            request.channelId = channel.id
            request.epgId = FieldText(channel,"epgId")
            request.epgUrl = m.epgUrl
            m.epgId = request.id
            m.epgTask = createObject("roSGNode","RequestTask")
            m.epgTask.observeField("response","onEpgResponse")
            m.epgTask.request = request
            m.epgTask.control = "run"
            return
        end if
    end while
end sub

sub onEpgResponse(event as object)
    response = event.getData()
    if not ResponseMatches(response,m.generation,m.epgId) then return
    m.epgTask = invalid
    m.epgLoaded[response.channelId] = NowSeconds()
    if response.ok
        if response.data.count() > 0 or not m.epg.doesExist(response.channelId)
            m.epg[response.channelId] = response.data
        end if
        m.guide.epg = m.epg
    else
        m.status.text = "Guide refresh couldn't finish. Previous guide kept. "+response.error
    end if
    nextEpg()
end sub

sub startPlayback(item as object, fullscreen as boolean)
    if item.kind <> "downloads" and item.kind <> "recordings" and hasCompanion()
        if m.playerItem <> invalid
            if m.playerItem.url = item.url and m.leaseExpires > NowSeconds() and (m.video.state = "playing" or m.video.state = "paused" or m.video.state = "buffering")
                playAuthorized(item,fullscreen)
                return
            end if
        end if
        stopPlayback()
        m.playbackSession = createObject("roDeviceInfo").getRandomUUID()
        m.pendingPlayback = {item:item,fullscreen:fullscreen}
        requestPlaybackLease()
        m.status.text = "Checking available provider streams…"
        return
    end if
    playAuthorized(item,fullscreen)
end sub

sub playAuthorized(item as object, fullscreen as boolean)
    if item.url = ""
        m.status.text = "No playable URL was supplied for this item."
        restoreFocus()
        return
    end if
    same = false
    if m.playerItem <> invalid then same = m.playerItem.url = item.url and (m.video.state = "playing" or m.video.state = "paused" or m.video.state = "buffering")
    if not same
        saveResume()
        if m.playerItem <> invalid
            if m.playerItem.kind = "live" then m.previousChannel = m.playerItem
        end if
        m.video.control = "stop"
        content = createObject("roSGNode","ContentNode")
        content.url = item.url
        content.title = item.title
        content.streamFormat = item.format
        content.httpCertificatesFile = "common:/certs/ca-bundle.crt"
        content.httpHeaders = ["User-Agent: RYZOD-Roku/0.1.0"]
        if FieldText(item,"agent") <> "" then content.httpHeaders = ["User-Agent: "+item.agent]
        if item.kind = "recordings" or item.kind = "downloads"
            content.httpHeaders = ["Authorization: Bearer "+m.store.settings.token]
        end if
        if item.kind = "live"
            content.live = true
            m.store.lastLive[PlaylistKey(activePlaylist())] = item.id
            saveStore()
        else
            key = ItemKey(activePlaylist(),item)
            if m.store.resume.doesExist(key) then content.playStart = m.store.resume[key].position
        end if
        m.playerItem = item
        m.video.content = content
        m.video.control = "play"
        m.bufferTicks = 0
    end if
    m.fullscreen = fullscreen
    if fullscreen
        m.shell.visible = false
        m.video.translation = [0,0]
        m.video.width = 1280
        m.video.height = 720
        m.video.visible = true
        m.top.setFocus(true)
        showMini()
    else
        setPreview()
    end if
end sub

sub setPreview()
    if m.playerItem <> invalid
        if m.playerItem.kind = "live" and m.video.state <> "stopped"
            m.video.translation = [994,72]
            m.video.width = 250
            m.video.height = 140
            m.video.visible = true
            return
        end if
    end if
    m.video.visible = false
end sub

sub returnFromPlayback()
    hideMini()
    m.fullscreen = false
    if m.playerItem <> invalid
        if m.playerItem.kind <> "live" then stopPlayback()
    end if
    renderHome()
    restoreFocus()
end sub

sub saveResume()
    if m.playerItem = invalid then return
    if m.playerItem.kind = "live" then return
    key = ItemKey(activePlaylist(),m.playerItem)
    resumePos = ResumePosition(m.video.position,m.video.duration)
    if resumePos > 0
        if m.store.resume.count() >= 80 and not m.store.resume.doesExist(key)
            oldestKey = ""
            oldestTime = NowSeconds()
            for each entryKey in m.store.resume
                if m.store.resume[entryKey].updated <= oldestTime
                    oldestTime = m.store.resume[entryKey].updated
                    oldestKey = entryKey
                end if
            end for
            if oldestKey <> "" then m.store.resume.delete(oldestKey)
        end if
        m.store.resume[key] = {position:resumePos,updated:NowSeconds()}
    else
        m.store.resume.delete(key)
    end if
    saveStore()
end sub

sub stopPlayback()
    saveResume()
    m.video.control = "stop"
    releasePlaybackLease()
    if m.leaseTask <> invalid then m.leaseTask.control = "stop"
    m.leaseTask = invalid
    m.leaseId = -1
    m.pendingPlayback = invalid
    m.leaseExpires = 0
    m.video.visible = false
    m.playerItem = invalid
    m.fullscreen = false
    hideMini()
    m.shell.visible = true
end sub

sub onVideoState()
    if m.video.state = "playing" then m.bufferTicks = 0
    if m.video.state = "finished"
        saveResume()
        m.fullscreen = false
        stopPlayback()
        renderHome()
        restoreFocus()
    else if m.video.state = "error"
        m.retryItem = m.playerItem
        stopPlayback()
        renderHome()
        openActions("Playback couldn't start","This stream may be unavailable or use a codec this Roku cannot play. You can retry, or change Live format in Settings.",[{id:"retryPlay",title:"Try again"},{id:"stopPlay",title:"Stop"},{id:"close",title:"Close"}],"playerError")
    end if
end sub

sub onPlaybackTick()
    m.playTicks = m.playTicks+1
    if m.playerItem = invalid then return
    if hasCompanion() and m.playerItem.kind <> "recordings" and m.playerItem.kind <> "downloads"
        if NowSeconds() >= m.leaseExpires
            stopPlayback()
            renderHome()
            m.status.text = "Playback stopped: companion stream reservation expired. Check its connection."
            restoreFocus()
            return
        end if
        if m.playTicks mod 3 = 0 then requestPlaybackLease()
    end if
    if m.video.state = "buffering"
        m.bufferTicks = m.bufferTicks+1
        if m.bufferTicks >= 40
            m.retryItem = m.playerItem
            stopPlayback()
            renderHome()
            openActions("Stream is taking too long","The stream hasn't started after 40 seconds. Retry it or choose another channel.",[{id:"retryPlay",title:"Try again"},{id:"stopPlay",title:"Stop"},{id:"close",title:"Close"}],"playerError")
        end if
    else
        m.bufferTicks = 0
    end if
    if m.playTicks mod 30 = 0 then saveResume()
    if m.fullscreen and m.mini.visible then drawMini()
end sub

sub showMini()
    if m.playerItem = invalid then return
    m.mini.visible = true
    m.miniIndex = 0
    m.miniTimer.control = "stop"
    m.miniTimer.control = "start"
    drawMini()
end sub

sub hideMini()
    m.mini.visible = false
    m.miniTimer.control = "stop"
end sub

sub drawMini()
    if m.playerItem = invalid then return
    ClearGroup(m.mini)
    UiBox(m.mini,0,504,1280,216,"0x07111FEE")
    UiBox(m.mini,28,512,1224,2,"0x49E8FFFF")
    title = m.playerItem.title
    if m.playerItem.kind = "live"
        if m.epg.doesExist(m.playerItem.id)
            cells = BuildGuideCells(m.epg[m.playerItem.id],NowSeconds(),NowSeconds()+7200)
            if cells.count() > 0 then title = title+" · "+cells[0].title
        end if
    end if
    UiLabel(m.mini,title,36,522,1200,48,24)
    status = "Playing"
    if m.video.state = "paused" then status = "Paused"
    if m.playerItem.kind = "live"
        if m.video.pauseBufferOverflow then status = status+" · Roku pause buffer is full; return to live if needed."
        status = status+" · "+ClockText(NowSeconds())
        span = m.video.pauseBufferEnd-m.video.pauseBufferStart
        if span > 0
            progress = (m.video.pauseBufferPosition-m.video.pauseBufferStart)/span
            if progress < 0 then progress = 0
            if progress > 1 then progress = 1
            UiBox(m.mini,36,577,1200,4,"0x29445FFF")
            UiBox(m.mini,36,577,1200*progress,4,"0xF5B944FF")
            status = status+" · Buffer "+int(span/60).toStr()+" min"
        end if
    else
        status = status+" · "+int(m.video.position/60).toStr()+" / "+int(m.video.duration/60).toStr()+" min"
        UiBox(m.mini,36,577,1200,4,"0x29445FFF")
        if m.video.duration > 0 then UiBox(m.mini,36,577,1200*m.video.position/m.video.duration,4,"0xF5B944FF")
    end if
    UiLabel(m.mini,status,36,582,1200,36,17,"0xBFEFFFFF")
    pauseLabel = "Pause"
    if m.video.state = "paused" then pauseLabel = "Play"
    m.miniActions = [{id:"pausePlay",title:pauseLabel}]
    if m.playerItem.kind = "live"
        m.miniActions.push({id:"returnLive",title:"Live"})
        m.miniActions.push({id:"rewind",title:"Rewind"})
        m.miniActions.push({id:"forward",title:"Forward"})
        m.miniActions.push({id:"previous",title:"Previous"})
        m.miniActions.push({id:"record",title:"Record"})
    else
        m.miniActions.push({id:"rewind",title:"Rewind"})
        m.miniActions.push({id:"forward",title:"Forward"})
    end if
    m.miniActions.push({id:"tracks",title:"Audio / CC"})
    m.miniActions.push({id:"playerOptions",title:"Options"})
    m.miniActions.push({id:"stopPlay",title:"Stop"})
    m.miniActions.push({id:"close",title:"Close"})
    if m.miniIndex >= m.miniActions.count() then m.miniIndex = 0
    width = 1208/m.miniActions.count()
    for i = 0 to m.miniActions.count()-1
        UiButton(m.mini,m.miniActions[i].title,36+i*width,637,width-6,52,m.miniIndex=i)
    end for
end sub

sub togglePause()
    if m.video.state = "paused"
        m.video.control = "resume"
    else
        m.video.control = "pause"
    end if
    showMini()
end sub

sub seekVideo(delta as integer)
    value = SeekTarget(m.playerItem.kind,m.video.position,m.video.duration,m.video.pauseBufferStart,m.video.pauseBufferEnd,m.video.pauseBufferPosition,delta)
    if value <> invalid then m.video.seek = value
    showMini()
end sub

sub showTracks()
    options = [{id:"ccOff",title:"Captions off"}]
    m.trackMap = {}
    for each track in m.video.availableAudioTracks
        id = "audio"+options.count().toStr()
        m.trackMap[id] = {kind:"audio",track:FieldText(track,"Track")}
        options.push({id:id,title:FieldText(track,"Name",FieldText(track,"Language","Audio"))})
    end for
    for each track in m.video.availableSubtitleTracks
        id = "subtitle"+options.count().toStr()
        m.trackMap[id] = {kind:"subtitle",track:FieldText(track,"TrackName",FieldText(track,"Track"))}
        options.push({id:id,title:FieldText(track,"Description",FieldText(track,"Language","Captions"))})
    end for
    options.push({id:"close",title:"Close"})
    openActions("Audio and captions","Choose a track offered by this stream.",options,"tracks")
end sub

function hasCompanion() as boolean
    return m.store.settings.companion <> "" and m.store.settings.token <> ""
end function

sub requestJobs()
    if not hasCompanion() then return
    if m.jobTask <> invalid then return
    request = newRequest("jobs")
    sendJobRequest(request)
end sub

sub sendJobRequest(request as object)
    if not hasCompanion()
        companionInfo()
        return
    end if
    if m.jobTask <> invalid
        m.status.text = "Wait for the current companion request to finish."
        return
    end if
    request.companion = m.store.settings.companion
    request.token = m.store.settings.token
    m.jobId = request.id
    m.jobTask = createObject("roSGNode","RequestTask")
    m.jobTask.observeField("response","onJobResponse")
    m.jobTask.request = request
    m.jobTask.control = "run"
end sub

sub onJobResponse(event as object)
    response = event.getData()
    if not ResponseMatches(response,m.generation,m.jobId) then return
    m.jobTask = invalid
    if not response.ok
        m.status.text = response.error
        restoreFocus()
        return
    end if
    if response.kind = "jobs"
        m.jobs = []
        if IsList(response.data.jobs)
            for each job in response.data.jobs
                item = {id:job.id,kind:job.kind,title:job.name,state:job.state,bytes:job.bytes,total:job.total,url:m.store.settings.companion+"/media/"+UrlEncode(job.id),format:job.format,desc:FieldText(job,"error"),icon:"",resumable:job.resumable,start:job.start,duration:job.duration}
                m.jobs.push(item)
            end for
        end if
        if m.section = "downloads" or m.section = "recordings" then renderHome()
    else if response.kind = "companionStatus"
        m.status.text = "Companion connected. Recordings and downloads are available."
    else
        m.status.text = "Saved on your companion."
        requestJobs()
    end if
    restoreFocus()
end sub

sub pollJobs()
    if m.mode <> "home" then return
    if m.section = "downloads" or m.section = "recordings" then requestJobs()
end sub

sub companionInfo()
    openActions("Set up recordings and downloads","Roku needs the optional RYZOD companion running on a computer or NAS for saved media. Enter its address and pairing token in Settings.",[{id:"settings",title:"Settings"},{id:"close",title:"Close"}],"companion")
end sub

sub prepareRecording(manual as boolean)
    if not hasCompanion()
        companionInfo()
        return
    end if
    if m.fullscreen then m.actionItem = m.playerItem
    if manual
        m.schedule.channelName = m.actionItem.title
        m.schedule.opened = true
        return
    end if
    now = NowSeconds()
    start = now
    duration = 3600
    if IsMap(m.actionProgram)
        if not m.actionProgram.gap
            programStart = m.actionProgram.start
            programEnd = m.actionProgram.end
            if m.actionProgram.programStart <> invalid then programStart = m.actionProgram.programStart
            if m.actionProgram.programEnd <> invalid then programEnd = m.actionProgram.programEnd
            if programStart > now then start = programStart
            duration = programEnd-start
        end if
    end if
    if duration < 60 then duration = 60
    if duration > 43200 then duration = 43200
    m.pendingJob = {kind:"recordings",name:m.actionItem.title,url:m.actionItem.url,start:start,duration:duration,format:"ts",agent:FieldText(m.actionItem,"agent"),scope:PlaylistKey(activePlaylist()),maxStreams:m.store.settings.streams}
    confirmRecording()
end sub

sub onScheduleSelected(event as object)
    selection = event.getData()
    m.pendingJob = {kind:"recordings",name:m.actionItem.title,url:m.actionItem.url,start:selection.start,duration:selection.duration,format:"ts",agent:FieldText(m.actionItem,"agent"),scope:PlaylistKey(activePlaylist()),maxStreams:m.store.settings.streams}
    confirmRecording()
end sub

sub confirmRecording()
    date = createObject("roDateTime")
    date.fromSeconds(m.pendingJob.start)
    date.toLocalTime()
    description = "Record "+m.pendingJob.name+" on "+date.asDateString("short-date")+" at "+ClockText(m.pendingJob.start)+" for "+int(m.pendingJob.duration/60).toStr()+" minutes?"
    if m.store.settings.streams = 1 then description = description+" Your 1-stream plan cannot record and watch at the same time."
    openActions("Confirm recording",description,[{id:"confirmRecord",title:"Record"},{id:"close",title:"Close"}],"confirmRecord")
end sub

sub prepareDownload()
    if not hasCompanion()
        companionInfo()
        return
    end if
    m.pendingJob = {kind:"downloads",name:m.actionItem.title,url:m.actionItem.url,start:NowSeconds(),duration:0,format:m.actionItem.format,agent:FieldText(m.actionItem,"agent"),scope:PlaylistKey(activePlaylist()),maxStreams:m.store.settings.streams}
    description = "Save "+m.actionItem.title+" on your companion?"
    if m.store.settings.streams = 1 then description = description+" Downloading uses your provider's only stream; playback will stop."
    openActions("Confirm download",description,[{id:"confirmDownload",title:"Download"},{id:"close",title:"Close"}],"confirmDownload")
end sub

sub createPendingJob()
    if m.store.settings.streams = 1 then stopPlayback()
    request = newRequest("newJob")
    request.job = m.pendingJob
    sendJobRequest(request)
end sub

sub jobAction(action as string)
    request = newRequest("jobAction")
    request.jobId = m.actionItem.id
    request.action = action
    sendJobRequest(request)
end sub

sub onActionSelected(event as object)
    id = event.getData()
    if id = "close"
        restoreFocus()
        return
    end if
    if m.actionContext = "tracks"
        if id = "ccOff"
            m.video.suppressCaptions = true
        else if m.trackMap.doesExist(id)
            track = m.trackMap[id]
            if track.kind = "audio"
                m.video.audioTrack = track.track
            else
                m.video.subtitleTrack = track.track
                m.video.globalCaptionMode = "On"
                m.video.suppressCaptions = false
            end if
        end if
    else if id = "playSeries" or id = "downloadSeries"
        m.seriesIntent = id
        request = newRequest("episodes")
        request.seriesId = m.selectedSeries.id
        startCatalogTask(request)
    else if id = "watch" or id = "play"
        startPlayback(m.actionItem,true)
    else if id = "favorite"
        toggleFavorite(m.actionItem)
        if not m.fullscreen then renderHome()
    else if id = "record"
        prepareRecording(false)
        return
    else if id = "manual"
        prepareRecording(true)
        return
    else if id = "download"
        prepareDownload()
        return
    else if id = "confirmRecord" or id = "confirmDownload"
        createPendingJob()
    else if id = "resume" or id = "pause"
        jobAction(id)
    else if id = "delete" or id = "stop"
        m.confirmAction = id
        title = "Are you sure you want to delete this?"
        if id = "stop" then title = "Are you sure you want to stop this recording?"
        openActions(title,m.actionItem.title,[{id:"confirmJob",title:"Yes"},{id:"close",title:"Close"}],"confirmJob")
        return
    else if id = "confirmJob"
        if m.playerItem <> invalid
            if m.playerItem.id = m.actionItem.id then stopPlayback()
        end if
        jobAction(m.confirmAction)
    else if id = "removePlaylist"
        openActions("Remove this playlist?",m.store.playlists[m.removeIndex].name,[{id:"confirmRemovePlaylist",title:"Remove"},{id:"close",title:"Close"}],"removePlaylist")
        return
    else if id = "confirmRemovePlaylist"
        m.store.playlists.delete(m.removeIndex)
        if m.store.active >= m.store.playlists.count() then m.store.active = 0
        saveStore()
        if m.store.playlists.count() = 0
            stopPlayback()
            cancelCatalog()
            cancelJobs()
            if m.epgTask <> invalid then m.epgTask.control = "stop"
            m.epgTask = invalid
            m.generation = m.generation+1
            m.previousChannel = invalid
            showLoginTypes()
        else
            activatePlaylist(m.store.active)
        end if
    else if id = "retryPlay"
        item = m.retryItem
        m.retryItem = invalid
        if item <> invalid then startPlayback(item,true)
    else if id = "stopPlay"
        stopPlayback()
        renderHome()
    else if id = "settings"
        selectSection(7)
    else if id = "exit"
        stopPlayback()
        m.top.exitRequested = true
    else if id = "seriesFavorite"
        toggleFavorite(m.selectedSeries)
    end if
    restoreFocus()
end sub

sub selectRow()
    if m.row < 0
        if m.view = "episodes"
            showSeasons()
        else if m.view = "seasons"
            m.view = "items"
            m.row = m.seriesReturnRow
            renderHome()
        else
            m.view = "categories"
            m.row = 0
            renderHome()
        end if
        return
    end if
    if m.rows.count() = 0 then return
    row = m.rows[m.row]
    kind = FieldText(row,"type")
    if m.mode = "loginType"
        beginLogin(kind)
    else if kind = "loginField"
        editText("login",row.key,row.title,m.draft[row.key],row.key="pass")
    else if kind = "connect"
        connectDraft()
    else if kind = "loginBack"
        showLoginTypes()
    else if kind = "category"
        m.category[m.section] = row.id
        m.view = "items"
        m.row = 0
        renderHome()
        if row.id <> "favorites"
            if not m.loaded.doesExist(m.section)
                requestCatalog(m.section,row.id)
            else if m.loaded[m.section] <> "all" and m.loaded[m.section] <> row.id
                requestCatalog(m.section,row.id)
            end if
        end if
    else if kind = "query"
        editText("search","query","Search",m.query)
    else if kind = "scope"
        scopes = ["live","movies","series"]
        index = 0
        for i = 0 to 2
            if scopes[i] = m.searchScope then index = i
        end for
        m.searchScope = scopes[WrapIndex(index+1,3)]
        m.row = 0
        renderHome()
        if m.query <> "" then requestCatalog(m.searchScope,"all")
    else if kind = "playlist"
        activatePlaylist(row.index)
    else if kind = "addPlaylist"
        showLoginTypes()
    else if kind = "setting"
        editSetting(row.key)
    else if kind = "season"
        showEpisodes(row.season)
    else if FieldText(row,"kind") <> ""
        showItem(row)
    end if
end sub

sub editSetting(key as string)
    if key = "companion" or key = "token"
        title = "Companion address (http://computer-ip:8787)"
        if key = "token" then title = "Companion pairing token"
        editText("setting",key,title,m.store.settings[key],key="token")
    else if key = "liveFormat"
        if m.store.settings.liveFormat = "hls"
            m.store.settings.liveFormat = "ts"
        else
            m.store.settings.liveFormat = "hls"
        end if
        saveStore()
        requestCatalog("live","all")
    else if key = "lastTune"
        m.store.settings.lastTune = not m.store.settings.lastTune
        saveStore()
        renderHome()
    else if key = "streams"
        m.store.settings.streams = 1+WrapIndex(m.store.settings.streams,3)
        saveStore()
        m.status.text = "Set this only to the number of streams your provider includes."
        renderHome()
    else if key = "test"
        sendJobRequest(newRequest("companionStatus"))
    else if key = "reload"
        activatePlaylist(m.store.active)
    else if key = "about"
        openActions("RYZOD Media Player","Roku 0.1.0, based on Android 4.70. Content comes from your own provider. Native pause length and codecs depend on Roku/stream. Saved DVR and downloads need the optional companion. Developer updates are sideloaded ZIPs.",[{id:"close",title:"Close"}],"about")
    end if
end sub

function onKeyEvent(key as string, press as boolean) as boolean
    if not press then return false
    if m.actions.visible or m.keyboard.visible or m.schedule.visible then return false
    if m.fullscreen
        if key = "back"
            if m.mini.visible
                hideMini()
            else
                returnFromPlayback()
            end if
        else if key = "play"
            togglePause()
        else if key = "rewind" or key = "replay"
            seekVideo(-10)
        else if key = "fastforward"
            seekVideo(30)
        else if key = "options"
            m.actionItem = m.playerItem
            if m.playerItem.kind = "live"
                m.actionProgram = {title:"Live TV",start:NowSeconds(),end:NowSeconds()+3600,gap:true}
            end if
            showItem(m.playerItem)
        else if key = "left" or key = "right"
            if not m.mini.visible
                showMini()
            else
                delta = 1
                if key = "left" then delta = -1
                m.miniIndex = WrapIndex(m.miniIndex+delta,m.miniActions.count())
                m.miniTimer.control = "stop"
                drawMini()
            end if
        else if key = "OK"
            if not m.mini.visible
                showMini()
            else
                action = m.miniActions[m.miniIndex].id
                if action = "pausePlay"
                    togglePause()
                else if action = "returnLive"
                    item = m.playerItem
                    m.playerItem = invalid
                    m.video.control = "stop"
                    startPlayback(item,true)
                else if action = "previous"
                    if m.previousChannel <> invalid then startPlayback(m.previousChannel,true)
                else if action = "record"
                    prepareRecording(false)
                else if action = "rewind"
                    seekVideo(-30)
                else if action = "forward"
                    seekVideo(30)
                else if action = "tracks"
                    showTracks()
                else if action = "playerOptions"
                    m.actionItem = m.playerItem
                    if m.playerItem.kind = "live" then m.actionProgram = {title:"Live TV",start:NowSeconds(),end:NowSeconds()+3600,gap:true}
                    showItem(m.playerItem)
                else if action = "stopPlay"
                    stopPlayback()
                    renderHome()
                    restoreFocus()
                else if action = "close"
                    hideMini()
                end if
            end if
        else if key = "up" or key = "down"
            showMini()
        else
            return false
        end if
        return true
    end if
    if key = "back"
        cancelCatalog()
        if m.mode = "login"
            cancelCatalog()
            showLoginTypes()
        else if m.mode = "loginType"
            if m.store.playlists.count() > 0
                m.mode = "home"
                renderHome()
            else
                showExit()
            end if
        else if m.view = "episodes"
            showSeasons()
            m.row = m.seasonReturnRow
            drawList()
        else if m.view = "seasons"
            m.view = "items"
            m.row = m.seriesReturnRow
            renderHome()
        else if m.view = "categories"
            m.view = "items"
            m.row = 0
            renderHome()
        else if m.focus = "content" and (m.section = "movies" or m.section = "series")
            m.view = "categories"
            m.row = 0
            renderHome()
        else if m.focus = "content"
            m.focus = "rail"
            renderRail()
            drawList()
        else
            showExit()
        end if
        return true
    end if
    if m.mode = "home" and m.focus = "rail"
        if key = "up"
            m.menuIndex = WrapIndex(m.menuIndex-1,8)
            renderRail()
        else if key = "down"
            m.menuIndex = WrapIndex(m.menuIndex+1,8)
            renderRail()
        else if key = "right" or key = "OK"
            selectSection(m.menuIndex)
        else if key = "play" and m.playerItem <> invalid
            startPlayback(m.playerItem,true)
        else
            return false
        end if
        return true
    end if
    if key = "left" and m.mode = "home"
        if isCatalogGrid() and m.row > 0 and m.row mod 3 <> 0
            m.row = m.row-1
            drawList()
        else
            m.focus = "rail"
            m.top.setFocus(true)
            renderRail()
            drawList()
        end if
    else if key = "up"
        if (m.section = "movies" or m.section = "series") and m.row = 0
            m.row = -1
        else if isCatalogGrid()
            if m.row < 3 then m.row = -1 else m.row = m.row-3
        else
            m.row = WrapIndex(m.row-1,m.rows.count())
        end if
        drawList()
    else if key = "down"
        if m.row = -1
            m.row = 0
        else if isCatalogGrid()
            m.row = WrapIndex(m.row+3,m.rows.count())
        else
            m.row = WrapIndex(m.row+1,m.rows.count())
        end if
        drawList()
    else if key = "right" and isCatalogGrid()
        if m.row < 0 then m.row = 0 else m.row = WrapIndex(m.row+1,m.rows.count())
        drawList()
    else if key = "OK"
        selectRow()
    else if key = "options"
        if m.section = "playlists" and m.rows.count() > 0
            row = m.rows[m.row]
            if row.type = "playlist"
                m.removeIndex = row.index
                openActions(row.title,"Remove this playlist from this Roku?",[{id:"removePlaylist",title:"Remove"},{id:"close",title:"Close"}],"playlist")
            end if
        else if m.section = "series" and m.view <> "items" and m.selectedSeries <> invalid
            openActions(m.selectedSeries.title,"Save this series as a favorite.",[{id:"seriesFavorite",title:"Favorite / unfavorite"},{id:"close",title:"Close"}],"seriesFavorite")
        else if m.section = "movies" or m.section = "series"
            m.view = "categories"
            m.row = 0
            renderHome()
        end if
    else
        return false
    end if
    return true
end function

sub showExit()
    openActions("Exit RYZOD?","Companion recordings and downloads continue while the companion is powered on.",[{id:"exit",title:"Exit"},{id:"close",title:"Close"}],"exit")
end sub


function isCatalogGrid() as boolean
    return m.mode = "home" and (m.section = "movies" or m.section = "series") and m.view = "items"
end function

sub drawCatalogGrid()
    first = 0
    if m.row >= 0 then first = int(m.row/9)*9
    for i = first to first+8
        if i < m.rows.count()
            item = m.rows[i]
            x = ((i-first) mod 3)*310
            y = 58+int((i-first)/3)*146
            focused = m.focus="content" and m.row=i
            UiButton(m.content,"",x,y,298,134,focused)
            icon = FieldText(item,"icon")
            if icon <> "" then UiPoster(m.content,icon,x+8,y+9,78,115)
            color = "0xF2F3F5FF"
            if focused then color = "0x49E8FFFF"
            title = item.title
            if isFavorite(item) then title = "★ "+title
            text = UiLabel(m.content,title,x+96,y+10,190,114,20,color)
            text.wrap = true
            text.numLines = 4
        end if
    end for
    UiLabel(m.content,(m.row+1).toStr()+" / "+m.rows.count().toStr()+"  ·  * Categories · Back to categories",8,484,900,26,17,"0xBFEFFFFF")
end sub

sub cancelJobs()
    if m.jobTask <> invalid then m.jobTask.control = "stop"
    m.jobTask = invalid
    m.jobId = -1
end sub

sub requestPlaybackLease()
    if m.leaseTask <> invalid then return
    request = newRequest("playbackLease")
    request.companion = m.store.settings.companion
    request.token = m.store.settings.token
    request.reservation = {client:m.playbackClient,session:m.playbackSession,scope:PlaylistKey(activePlaylist()),maxStreams:m.store.settings.streams}
    m.leaseId = request.id
    m.leaseStarted = NowSeconds()
    m.leaseTask = createObject("roSGNode","RequestTask")
    m.leaseTask.observeField("response","onPlaybackLease")
    m.leaseTask.request = request
    m.leaseTask.control = "run"
end sub

sub onPlaybackLease(event as object)
    response = event.getData()
    if not ResponseMatches(response,m.generation,m.leaseId) then return
    m.leaseTask = invalid
    if not response.ok
        stopPlayback()
        renderHome()
        m.status.text = "Playback stopped: companion is unavailable. Check its address and pairing token."
        restoreFocus()
        return
    end if
    if response.data.allowed <> true
        stopPlayback()
        renderHome()
        m.status.text = "Provider streams are reserved for a recording or download. Playback can resume when a stream is free."
        restoreFocus()
        return
    end if
    m.leaseExpires = m.leaseStarted+7
    if m.leaseExpires <= NowSeconds()
        stopPlayback()
        renderHome()
        m.status.text = "Companion response arrived too late. Check its connection and try again."
        restoreFocus()
        return
    end if
    if m.pendingPlayback <> invalid
        pending = m.pendingPlayback
        m.pendingPlayback = invalid
        playAuthorized(pending.item,pending.fullscreen)
    end if
end sub

sub releasePlaybackLease()
    if not hasCompanion() then return
    if m.leaseTask = invalid and m.leaseExpires <= 0 and m.pendingPlayback = invalid then return
    if m.releaseTask <> invalid then return
    request = newRequest("playbackLease")
    request.companion = m.store.settings.companion
    request.token = m.store.settings.token
    request.reservation = {action:"release",client:m.playbackClient,session:m.playbackSession,scope:PlaylistKey(activePlaylist()),maxStreams:m.store.settings.streams}
    m.releaseId = request.id
    m.releaseTask = createObject("roSGNode","RequestTask")
    m.releaseTask.observeField("response","onLeaseReleased")
    m.releaseTask.request = request
    m.releaseTask.control = "run"
end sub

sub onLeaseReleased(event as object)
    response = event.getData()
    if response.id = m.releaseId then m.releaseTask = invalid
end sub
