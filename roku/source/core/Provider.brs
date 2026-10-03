' RYZOD provider normalization; shared by SceneGraph tasks and host tests.
function IsMap(value as dynamic) as boolean
    return type(value) = "roAssociativeArray" or type(value) = "AssociativeArray"
end function

function IsList(value as dynamic) as boolean
    return type(value) = "roArray" or type(value) = "Array"
end function

function TextValue(value as dynamic, fallback = "" as string) as string
    if value = invalid then return fallback
    if type(value) = "String" or type(value) = "roString" then return value
    if type(value) = "Integer" or type(value) = "roInt" or type(value) = "Float" or type(value) = "Double" or type(value) = "LongInteger" then return value.toStr().trim()
    return fallback
end function

function FieldText(value as dynamic, key as string, fallback = "" as string) as string
    if not IsMap(value) then return fallback
    out = TextValue(value[key], fallback)
    if out = "" then return fallback
    return out
end function

function UrlEncode(value as string) as string
    bytes = createObject("roByteArray")
    bytes.fromAsciiString(value)
    hex = "0123456789ABCDEF"
    result = ""
    for byteIndex = 0 to bytes.count()-1
        b = bytes[byteIndex]
        if (b >= 65 and b <= 90) or (b >= 97 and b <= 122) or (b >= 48 and b <= 57) or b = 45 or b = 46 or b = 95 or b = 126
            result = result + chr(b)
        else
            result = result + "%" + mid(hex, int(b / 16) + 1, 1) + mid(hex, (b mod 16) + 1, 1)
        end if
    end for
    return result
end function

function IsWebUrl(value as string) as boolean
    lower = lcase(value)
    if left(lower, 7) <> "http://" and left(lower, 8) <> "https://" then return false
    if instr(1, value, chr(10)) > 0 or instr(1, value, chr(13)) > 0 or instr(1, value, " ") > 0 then return false
    start = instr(1, value, "://") + 3
    return len(value) > start - 1
end function

function NormalizeHost(raw as string) as string
    host = raw.trim()
    if host = "" then return ""
    if instr(1, host, "://") = 0 then host = "http://" + host
    if not IsWebUrl(host) then return ""
    q = instr(1, host, "?")
    if q > 0 then host = left(host, q - 1)
    for each suffix in ["/player_api.php", "/get.php", "/xmltv.php"]
        if right(lcase(host), len(suffix)) = suffix then host = left(host, len(host) - len(suffix))
    end for
    while right(host, 1) = "/"
        host = left(host, len(host) - 1)
    end while
    return host
end function

function ApiUrl(playlist as object, action as string, params = invalid as dynamic) as string
    result = NormalizeHost(FieldText(playlist, "host")) + "/player_api.php?username=" + UrlEncode(FieldText(playlist, "user")) + "&password=" + UrlEncode(FieldText(playlist, "pass"))
    if action <> "" then result = result + "&action=" + UrlEncode(action)
    if IsMap(params)
        for each key in params
            result = result + "&" + UrlEncode(key) + "=" + UrlEncode(TextValue(params[key]))
        end for
    end if
    return result
end function

function StreamUrl(playlist as object, kind as string, id as string, extension as string) as string
    return NormalizeHost(FieldText(playlist, "host")) + "/" + kind + "/" + UrlEncode(FieldText(playlist, "user")) + "/" + UrlEncode(FieldText(playlist, "pass")) + "/" + UrlEncode(id) + "." + UrlEncode(extension)
end function

function CatalogRows(raw as dynamic) as object
    if IsList(raw) then return {ok: true, rows: raw}
    if IsMap(raw)
        for each key in ["data", "results", "streams", "movies", "vod", "series"]
            if IsList(raw[key]) then return {ok: true, rows: raw[key]}
        end for
    end if
    return {ok: false, rows: [], error: "Your provider returned an unexpected catalog. Try refreshing."}
end function

function NormalizeCategories(raw as dynamic) as object
    result = []
    parsed = CatalogRows(raw)
    if not parsed.ok then return result
    for each row in parsed.rows
        if IsMap(row)
            result.push({id: FieldText(row, "category_id"), title: FieldText(row, "category_name", "Other")})
        end if
    end for
    return result
end function

function StreamFormat(url as string) as string
    clean = lcase(url)
    q = instr(1, clean, "?")
    if q > 0 then clean = left(clean, q - 1)
    if right(clean, 5) = ".m3u8" then return "hls"
    if right(clean, 4) = ".mpd" then return "dash"
    if right(clean, 3) = ".ts" then return "ts"
    if right(clean, 4) = ".mkv" then return "mkv"
    return "mp4"
end function

function NormalizeCatalog(rows as object, kind as string, playlist as object, liveFormat = "hls" as string) as object
    result = []
    seen = {}
    for each row in rows
        if IsMap(row)
            id = FieldText(row, "stream_id")
            if kind = "series" then id = FieldText(row, "series_id")
            if id <> "" and not seen.doesExist(id)
                seen[id] = true
                title = FieldText(row, "name", "Channel")
                item = {id: id, kind: kind, title: title, category: FieldText(row, "category_id"), icon: FieldText(row, "stream_icon", FieldText(row, "cover")), desc: FieldText(row, "plot"), url: "", format: "", agent: "", epgId: FieldText(row, "epg_channel_id"), season: 0, episode: 0}
                if kind = "live"
                    ext = "m3u8"
                    if liveFormat = "ts" then ext = "ts"
                    item.url = StreamUrl(playlist, "live", id, ext)
                else if kind = "movies"
                    item.url = StreamUrl(playlist, "movie", id, FieldText(row, "container_extension", "mp4"))
                end if
                item.format = StreamFormat(item.url)
                result.push(item)
            end if
        end if
    end for
    return result
end function

function M3uAttributes(line as string) as object
    attrs = {}
    regex = createObject("roRegex", "([a-zA-Z0-9_-]+)=" + chr(34) + "([^" + chr(34) + "]*)" + chr(34), "")
    matches = regex.matchAll(line)
    for each match in matches
        if match.count() >= 3 then attrs[lcase(match[1])] = match[2]
    end for
    return attrs
end function

function M3uTitle(line as string) as string
    quoted = false
    for i = 1 to len(line)
        c = mid(line, i, 1)
        if c = chr(34) then quoted = not quoted
        if c = "," and not quoted then return mid(line, i + 1).trim()
    end for
    return "Channel"
end function

function ParseM3u(raw as string) as object
    items = []
    attrs = invalid
    title = ""
    epgUrl = ""
    agent = ""
    seen = {}
    for each rawLine in raw.split(chr(10))
        line = rawLine.replace(chr(13), "").replace(chr(65279), "").trim()
        if left(line, 7) = "#EXTM3U"
            header = M3uAttributes(line)
            epgUrl = FieldText(header, "x-tvg-url", FieldText(header, "url-tvg"))
            comma = instr(1, epgUrl, ",")
            if comma > 0 then epgUrl = left(epgUrl, comma - 1)
        else if left(line, 7) = "#EXTINF"
            attrs = M3uAttributes(line)
            title = M3uTitle(line)
            agent = ""
        else if left(line, 30) = "#EXTVLCOPT:http-user-agent="
            agent = mid(line, 27)
        else if instr(1, line, "#EXTVLCOPT:http-user-agent=") = 1
            agent = mid(line, len("#EXTVLCOPT:http-user-agent=") + 1)
        else if IsWebUrl(line) and IsMap(attrs)
            if not seen.doesExist(line)
                seen[line] = true
                kind = "live"
                format = StreamFormat(line)
                cleanUrl = lcase(line).split("?")[0]
                mediaType = lcase(FieldText(attrs,"type"))
                if right(cleanUrl,4) = ".mp4" or right(cleanUrl,4) = ".mkv" or right(cleanUrl,4) = ".m4v" or mediaType = "vod" or mediaType = "movie" then kind = "movies"
                if kind = "live" and format = "mp4" then format = "hls"
                ' Stable URL IDs survive reorder; provider tvg-id is reserved for guide lookup.
                items.push({id: line, kind: kind, title: title, category: FieldText(attrs, "group-title", "Other"), icon: FieldText(attrs, "tvg-logo"), epgId: FieldText(attrs, "tvg-id"), desc: "", url: line, format: format, agent: agent, season: 0, episode: 0})
            end if
            attrs = invalid
        end if
    end for
    return {ok: items.count() > 0, items: items, epgUrl: epgUrl, error: "The link did not return a playable M3U playlist."}
end function

function ParseEpisodes(raw as dynamic, playlist as object) as object
    result = []
    if not IsMap(raw) then return result
    episodes = raw.episodes
    if IsList(episodes)
        AppendEpisodes(result, episodes, playlist, 0)
    else if IsMap(episodes)
        for each key in episodes
            if IsList(episodes[key]) then AppendEpisodes(result, episodes[key], playlist, val(key))
        end for
    end if
    ' Provider season keys are strings: numeric ordering is essential (2 before 10).
    for i = 1 to result.count() - 1
        item = result[i]
        j = i - 1
        while j >= 0
            prior = result[j]
            if prior.season < item.season then exit while
            if prior.season = item.season and prior.episode <= item.episode then exit while
            result[j + 1] = prior
            j = j - 1
        end while
        result[j + 1] = item
    end for
    return result
end function

sub AppendEpisodes(result as object, rows as object, playlist as object, fallbackSeason as integer)
    for each row in rows
        if IsMap(row)
            info = row.info
            id = FieldText(row, "id", FieldText(row, "episode_id", FieldText(row, "stream_id")))
            if id <> ""
                season = val(FieldText(row, "season", FieldText(info, "season", fallbackSeason.toStr())))
                number = val(FieldText(row, "episode_num", FieldText(info, "episode_num", (result.count() + 1).toStr())))
                url = StreamUrl(playlist, "series", id, FieldText(row, "container_extension", FieldText(info, "container_extension", "mp4")))
                result.push({id: id, kind: "episode", title: FieldText(row, "title", FieldText(info, "title", "Episode " + number.toStr())), category: season.toStr(), season: season, episode: number, url: url, format: StreamFormat(url), icon: FieldText(info, "movie_image"), desc: FieldText(row, "plot", FieldText(info, "plot")), agent: "", epgId: ""})
            end if
        end if
    end for
end sub
