function EpochValue(raw as dynamic) as integer
    if raw = invalid then return 0
    text = TextValue(raw)
    if instr(1, text, "-") = 0 then return int(val(text))
    date = createObject("roDateTime")
    date.fromISO8601String(text.replace(" ", "T") + "Z")
    return date.asSeconds()
end function

function DecodeGuideText(text as string) as string
    if text = "" then return ""
    regex = createObject("roRegex", "^[A-Za-z0-9+/]+={0,2}$", "")
    if len(text) mod 4 <> 0 or not regex.isMatch(text) then return text
    bytes = createObject("roByteArray")
    bytes.fromBase64String(text)
    if bytes.count() = 0 then return text
    for each b in bytes
        if b < 32 and b <> 9 and b <> 10 and b <> 13 then return text
    end for
    decoded = bytes.toAsciiString()
    if instr(1, decoded, chr(65533)) > 0 then return text
    return decoded
end function

function ParseEpg(raw as dynamic) as object
    result = []
    if not IsMap(raw) then return result
    if not IsList(raw.epg_listings) then return result
    for each row in raw.epg_listings
        if IsMap(row)
            start = EpochValue(row.start_timestamp)
            finish = EpochValue(row.stop_timestamp)
            if start <= 0 then start = EpochValue(row.start)
            if finish <= 0 then finish = EpochValue(row.end)
            if finish <= 0 then finish = EpochValue(row.stop)
            if start > 0
                if finish <= start then finish = start + 1800
                result.push({start: start, end: finish, title: DecodeGuideText(FieldText(row, "title")), desc: DecodeGuideText(FieldText(row, "description")), gap: false})
            end if
        end if
    end for
    return SortPrograms(result)
end function

function ParseXmltvTime(text as string) as integer
    if len(text) < 14 then return 0
    stamp = left(text,4)+"-"+mid(text,5,2)+"-"+mid(text,7,2)+"T"+mid(text,9,2)+":"+mid(text,11,2)+":"+mid(text,13,2)+"Z"
    date = createObject("roDateTime")
    date.fromISO8601String(stamp)
    result = date.asSeconds()
    if len(text) >= 20
        sign = mid(text,16,1)
        offset = (val(mid(text,17,2))*60 + val(mid(text,19,2)))*60
        if sign = "+" then result = result - offset
        if sign = "-" then result = result + offset
    end if
    return int(result)
end function

function ParseXmltv(raw as string, channelId as string) as object
    result = []
    xml = createObject("roXMLElement")
    if not xml.parse(raw) then return result
    nodes = xml.getNamedElements("programme")
    for each node in nodes
        attrs = node.getAttributes()
        if FieldText(attrs,"channel") = channelId
            start = ParseXmltvTime(FieldText(attrs,"start"))
            finish = ParseXmltvTime(FieldText(attrs,"stop"))
            if start > 0 and finish > start
                title = ""
                desc = ""
                titles = node.getNamedElements("title")
                descriptions = node.getNamedElements("desc")
                if titles.count() > 0 then title = titles[0].getText()
                if descriptions.count() > 0 then desc = descriptions[0].getText()
                result.push({start: start, end: finish, title: title, desc: desc, gap: false})
            end if
        end if
    end for
    return SortPrograms(result)
end function

function SortPrograms(programs as object) as object
    ' Sort a shallow copy; no mutation of the cached provider response.
    result = []
    for each program in programs
        if IsMap(program)
            if program.start <> invalid and program.end <> invalid then result.push(program)
        end if
    end for
    for i = 1 to result.count() - 1
        item = result[i]
        j = i - 1
        while j >= 0
            if result[j].start <= item.start then exit while
            result[j+1] = result[j]
            j = j - 1
        end while
        result[j+1] = item
    end for
    return result
end function

function BuildGuideCells(programs as object, windowStart as integer, windowEnd as integer) as object
    result = []
    cursor = windowStart
    for each program in SortPrograms(programs)
        if program.end > cursor and program.start < windowEnd
            start = program.start
            finish = program.end
            if start < cursor then start = cursor
            if finish > windowEnd then finish = windowEnd
            if start > cursor then result.push({start:cursor, end:start, title:"No guide data", desc:"You can record this channel by day and time, up to one week ahead.", gap:true})
            if finish > start
                result.push({start:start, end:finish, programStart:program.start, programEnd:program.end, title:FieldText(program,"title","Untitled program"), desc:FieldText(program,"desc"), gap:false})
                cursor = finish
            end if
        end if
    end for
    if cursor < windowEnd then result.push({start:cursor, end:windowEnd, title:"No guide data", desc:"You can record this channel by day and time, up to one week ahead.", gap:true})
    return result
end function

function GuideCellIndex(cells as object, point as integer) as integer
    for i = 0 to cells.count()-1
        if point >= cells[i].start and point < cells[i].end then return i
    end for
    return 0
end function

function ValidateSchedule(now as integer, start as integer, durationMinutes as integer) as object
    if start <= now then return {ok:false,error:"Choose a future start time."}
    if start > now + 604800 then return {ok:false,error:"Choose a start within the next seven days."}
    if start mod 300 <> 0 then return {ok:false,error:"Choose a five-minute time step."}
    if durationMinutes < 5 or durationMinutes > 720 then return {ok:false,error:"Choose a duration between 5 minutes and 12 hours."}
    return {ok:true,error:""}
end function

function ClockText(epoch as integer) as string
    date = createObject("roDateTime")
    date.fromSeconds(epoch)
    date.toLocalTime()
    hour = date.getHours()
    suffix = " AM"
    if hour >= 12 then suffix = " PM"
    hour = hour mod 12
    if hour = 0 then hour = 12
    minute = date.getMinutes().toStr()
    if len(minute) < 2 then minute = "0" + minute
    return hour.toStr() + ":" + minute + suffix
end function

function ScheduleLocalTime(year as integer, month as integer, day as integer, hour as integer, minute as integer, now as integer) as integer
    date = createObject("roDateTime")
    date.fromISO8601String(year.toStr()+"-"+right("0"+month.toStr(),2)+"-"+right("0"+day.toStr(),2)+"T"+right("0"+hour.toStr(),2)+":"+right("0"+minute.toStr(),2)+":00Z")
    nominal = date.asSeconds()
    candidate = nominal
    ' Re-evaluate the offset at the target instant rather than adding today's offset.
    for i = 0 to 2
        probe = createObject("roDateTime")
        probe.fromSeconds(candidate)
        probe.toLocalTime()
        candidate = nominal + candidate-probe.asSeconds()
    end for
    return candidate
end function
