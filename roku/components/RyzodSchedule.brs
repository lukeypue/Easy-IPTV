sub init()
    m.body = m.top.findNode("body")
    m.index = 0
end sub

sub openSchedule()
    if not m.top.opened then return
    m.day = 0
    local = createObject("roDateTime")
    local.toLocalTime()
    m.hour = local.getHours()
    m.minute = int(local.getMinutes()/5)*5 + 5
    if m.minute >= 60
        m.minute = 0
        m.hour = m.hour + 1
    end if
    if m.hour >= 24
        m.hour = 0
        m.day = 1
    end if
    m.duration = 60
    m.index = 0
    m.error = ""
    m.top.visible = true
    m.top.setFocus(true)
    drawSchedule()
end sub

function scheduleStart() as integer
    ' Let the device resolve local DST for the selected civil day/hour.
    day = createObject("roDateTime")
    day.fromSeconds(NowSeconds()+m.day*86400)
    day.toLocalTime()
    return ScheduleLocalTime(day.getYear(),day.getMonth(),day.getDayOfMonth(),m.hour,m.minute,NowSeconds())
end function

sub drawSchedule()
    ClearGroup(m.body)
    UiBox(m.body,0,0,1280,720,"0x000000DD")
    UiBox(m.body,100,145,1080,430,"0x101C2DFF")
    UiLabel(m.body,"Record by time · "+m.top.channelName,140,160,1000,60,27)
    UiLabel(m.body,"Left/right: choose a spinner. Up/down: change it. OK: confirm.",140,225,1000,45,19,"0xBFEFFFFF")
    stamp = createObject("roDateTime")
    stamp.fromSeconds(NowSeconds()+m.day*86400)
    stamp.toLocalTime()
    dayLabel = stamp.asDateString("short-date")
    values = [dayLabel,right("0"+m.hour.toStr(),2)+" hour",right("0"+m.minute.toStr(),2)+" minute",m.duration.toStr()+" min"]
    names = ["Day (7 days)","Hour (0–23)","Minute (5 min)","Duration"]
    for i = 0 to 3
        UiLabel(m.body,names[i],140+i*245,285,230,45,18,"0xBFEFFFFF")
        UiButton(m.body,values[i],140+i*245,340,230,70,m.index=i)
    end for
    UiLabel(m.body,m.error,140,425,1000,55,20,"0xFF3B5CFF")
    UiButton(m.body,"Continue",140,495,300,55,m.index=4)
    UiButton(m.body,"Close",470,495,300,55,m.index=5)
end sub

function onKeyEvent(key as string, press as boolean) as boolean
    if not press then return false
    if key = "back"
        m.top.visible = false
        m.top.cancelled = true
        return true
    else if key = "left"
        m.index = WrapIndex(m.index-1,6)
    else if key = "right"
        m.index = WrapIndex(m.index+1,6)
    else if key = "up" or key = "down"
        delta = 1
        if key = "down" then delta = -1
        if m.index = 0 then m.day = WrapIndex(m.day+delta,7)
        if m.index = 1 then m.hour = WrapIndex(m.hour+delta,24)
        if m.index = 2 then m.minute = WrapIndex(int(m.minute/5)+delta,12)*5
        if m.index = 3 then m.duration = 5+WrapIndex(int(m.duration/5)-1+delta,144)*5
        if m.index >= 4 then m.index = 0
    else if key = "OK"
        if m.index = 5
            m.top.visible = false
            m.top.cancelled = true
            return true
        end if
        start = scheduleStart()
        check = ValidateSchedule(NowSeconds(),start,m.duration)
        if check.ok
            m.top.visible = false
            m.top.selected = {start:start,duration:m.duration*60,label:ClockText(start)}
            return true
        end if
        m.error = check.error
    else
        return false
    end if
    drawSchedule()
    return true
end function
