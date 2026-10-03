sub init()
    m.top.functionName = "runRequest"
end sub

sub runRequest()
    r = m.top.request
    response = {id:r.id,generation:r.generation,kind:r.kind,ok:false,error:"Couldn't reach your service. Check the address and try again.",data:invalid,channelId:FieldText(r,"channelId")}
    p = r.playlist
    format = FieldText(r,"liveFormat","hls")
    if r.kind = "connect"
        if p.type = "m3u"
            net = RequestHttp(p.url)
            if net.ok
                data = ParseM3u(net.body)
                if data.ok then response = SuccessResponse(response,data)
            else
                response.error = net.error
            end if
        else
            net = RequestHttp(ApiUrl(p,""))
            if net.ok
                info = parseJson(net.body)
                if IsMap(info)
                    if FieldText(info.user_info,"auth") = "1"
                        response = SuccessResponse(response,{})
                    else
                        response.error = "Server reached, but the username or password was rejected."
                    end if
                end if
            else
                response.error = net.error
            end if
        end if
    else if r.kind = "catalog"
        kind = r.mediaKind
        if p.type = "m3u"
            net = RequestHttp(p.url)
            if net.ok
                data = ParseM3u(net.body)
                if data.ok then response = SuccessResponse(response,data)
            else
                response.error = net.error
            end if
        else
            action = "get_live_streams"
            catAction = "get_live_categories"
            if kind = "movies"
                action = "get_vod_streams"
                catAction = "get_vod_categories"
            else if kind = "series"
                action = "get_series"
                catAction = "get_series_categories"
            end if
            cats = []
            catResponse = RequestHttp(ApiUrl(p,catAction))
            if catResponse.ok then cats = NormalizeCategories(parseJson(catResponse.body))
            params = {}
            category = FieldText(r,"category")
            if category <> "" and category <> "all" and category <> "favorites" then params.category_id = category
            net = RequestHttp(ApiUrl(p,action,params))
            if net.ok
                parsed = CatalogRows(parseJson(net.body))
                if parsed.ok
                    items = NormalizeCatalog(parsed.rows,kind,p,format)
                    response = SuccessResponse(response,{items:items,categories:cats,mediaKind:kind,category:category})
                else
                    response.error = parsed.error
                end if
            else
                response.error = net.error
            end if
        end if
    else if r.kind = "episodes"
        net = RequestHttp(ApiUrl(p,"get_series_info",{series_id:r.seriesId}))
        if net.ok
            raw = parseJson(net.body)
            if IsMap(raw) then response = SuccessResponse(response,{items:ParseEpisodes(raw,p),info:raw.info})
        else
            response.error = net.error
        end if
    else if r.kind = "epg"
        if p.type = "m3u"
            url = FieldText(r,"epgUrl")
            if url <> "" and FieldText(r,"epgId") <> ""
                net = RequestHttp(url)
                if net.ok then response = SuccessResponse(response,ParseXmltv(net.body,r.epgId))
            else
                response.error = "This playlist doesn't include a guide link and channel guide ID."
            end if
        else
            net = RequestHttp(ApiUrl(p,"get_short_epg",{stream_id:r.channelId,limit:48}))
            programs = []
            if net.ok then programs = ParseEpg(parseJson(net.body))
            if programs.count() = 0
                net = RequestHttp(ApiUrl(p,"get_simple_data_table",{stream_id:r.channelId}))
                if net.ok then programs = ParseEpg(parseJson(net.body))
            end if
            if net.ok
                response = SuccessResponse(response,programs)
            else
                response.error = net.error
            end if
        end if
    else if r.kind = "jobs" or r.kind = "jobAction" or r.kind = "newJob" or r.kind = "companionStatus" or r.kind = "playbackLease"
        method = "GET"
        endpoint = "/api/jobs"
        body = ""
        if r.kind = "companionStatus" then endpoint = "/api/status"
        if r.kind = "playbackLease"
            endpoint = "/api/playback"
            method = "POST"
            body = formatJson(r.reservation)
        end if
        if r.kind = "jobAction"
            method = "POST"
            endpoint = "/api/jobs/" + UrlEncode(r.jobId) + "/" + UrlEncode(r.action)
            body = "{}"
        else if r.kind = "newJob"
            method = "POST"
            body = formatJson(r.job)
        end if
        net = RequestHttp(r.companion + endpoint,method,body,r.token)
        if net.ok
            data = parseJson(net.body)
            if IsMap(data) then response = SuccessResponse(response,data)
        else
            response.error = net.error
        end if
    end if
    m.top.response = response
end sub

function SuccessResponse(response as object, data as dynamic) as object
    response.ok = true
    response.error = ""
    response.data = data
    return response
end function

function RequestHttp(url as string, method = "GET" as string, body = "" as string, token = "" as string) as object
    if not IsWebUrl(url) then return {ok:false,body:"",error:"Enter a complete http:// or https:// address."}
    transfer = createObject("roUrlTransfer")
    port = createObject("roMessagePort")
    transfer.setMessagePort(port)
    transfer.setUrl(url)
    transfer.setCertificatesFile("common:/certs/ca-bundle.crt")
    transfer.initClientCertificates()
    transfer.addHeader("User-Agent","RYZOD-Roku/0.1.0")
    transfer.enableEncodings(true)
    if token <> "" then transfer.addHeader("Authorization","Bearer " + token)
    if method = "POST"
        transfer.addHeader("Content-Type","application/json")
        started = transfer.asyncPostFromString(body)
    else
        started = transfer.asyncGetToString()
    end if
    if not started then return {ok:false,body:"",error:"Couldn't start the request. Try again."}
    ' Explicit timeout applies even to servers which connect but never finish.
    timer = createObject("roTimespan")
    timer.mark()
    while timer.totalMilliseconds() < 20000
        msg = wait(250,port)
        if type(msg) = "roUrlEvent"
            code = msg.getResponseCode()
            if code >= 200 and code < 300
                text = msg.getString()
                if len(text) > 16777216 then return {ok:false,body:"",error:"This catalog is too large for this Roku. Choose a smaller provider playlist."}
                return {ok:true,body:text,error:""}
            end if
            return {ok:false,body:"",error:"Service returned HTTP " + code.toStr() + ". Check your login or connection."}
        end if
    end while
    transfer.asyncCancel()
    return {ok:false,body:"",error:"The service took too long to respond. Try again."}
end function
