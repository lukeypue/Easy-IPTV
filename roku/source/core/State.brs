function WrapIndex(value as integer, count as integer) as integer
    if count <= 0 then return 0
    return ((value mod count) + count) mod count
end function

function PlaylistKey(playlist as object) as string
    if FieldText(playlist,"type") = "m3u" then return "m3u|" + FieldText(playlist,"url")
    return "xtream|" + NormalizeHost(FieldText(playlist,"host")) + "|" + FieldText(playlist,"user")
end function

function ItemKey(playlist as object, item as object) as string
    ' Length-prefixed parts avoid delimiter collisions in user names and URL IDs.
    scope = PlaylistKey(playlist)
    kind = FieldText(item,"kind")
    id = FieldText(item,"id")
    return len(scope).toStr()+":"+scope+len(kind).toStr()+":"+kind+len(id).toStr()+":"+id
end function

function ResumePosition(position as float, duration as float) as integer
    if position < 10 then return 0
    if duration > 0 and position >= duration - 30 then return 0
    return int(position)
end function

function ResponseMatches(response as dynamic, generation as integer, requestId as integer) as boolean
    if not IsMap(response) then return false
    return response.generation = generation and response.id = requestId
end function

function FilterItems(items as object, category as string, query as string) as object
    result = []
    query = lcase(query.trim())
    for each item in items
        matches = category = "all" or category = "" or FieldText(item,"category") = category
        if matches and (query = "" or instr(1,lcase(FieldText(item,"title")+" "+FieldText(item,"desc")),query) > 0) then result.push(item)
    end for
    return result
end function

function ItemActions(kind as string, state as string, favorite as boolean) as object
    result = []
    if kind = "live"
        result.push({id:"watch",title:"Watch"})
        result.push({id:"record",title:"Record"})
        result.push({id:"manual",title:"By time"})
    else
        result.push({id:"play",title:"Play"})
        if kind = "movies" or kind = "episode" then result.push({id:"download",title:"Download"})
    end if
    if kind = "recordings" or kind = "downloads"
        if state = "queued" or state = "paused" or state = "retry" or state = "failed" then result.push({id:"resume",title:"Resume"})
        if kind = "downloads" and (state = "running" or state = "queued") then result.push({id:"pause",title:"Pause download"})
        if kind = "recordings" and (state = "running" or state = "scheduled" or state = "retry") then result.push({id:"stop",title:"Stop recording"})
        result.push({id:"delete",title:"Delete"})
    else
        title = "Favorite"
        if favorite then title = "Unfavorite"
        result.push({id:"favorite",title:title})
    end if
    result.push({id:"close",title:"Close"})
    return result
end function

function SeekTarget(kind as string, position as float, duration as float, bufferStart as float, bufferEnd as float, bufferPosition as float, delta as integer) as dynamic
    if kind = "live"
        if bufferEnd <= bufferStart then return invalid
        value = bufferPosition+delta
        if value < bufferStart then value = bufferStart
        if value > bufferEnd then value = bufferEnd
        return value
    end if
    value = position+delta
    if value < 0 then value = 0
    if duration > 0 and value > duration-1 then value = duration-1
    return value
end function
