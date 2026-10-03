sub init()
    m.body = m.top.findNode("body")
    m.index = 0
end sub

sub openActions()
    if not m.top.opened then return
    m.index = 0
    m.top.visible = true
    m.top.setFocus(true)
    drawActions()
end sub

sub drawActions()
    ClearGroup(m.body)
    UiBox(m.body,0,0,1280,720,"0x000000CC")
    UiBox(m.body,80,230,1120,280,"0x49E8FFFF")
    UiBox(m.body,83,233,1114,274,"0x101C2DFF")
    UiLabel(m.body,m.top.title,110,245,1060,52,27)
    desc = UiLabel(m.body,m.top.description,110,300,1060,100,18,"0xBFEFFFFF")
    desc.wrap = true
    desc.numLines = 3
    count = m.top.actions.count()
    if count = 0 then return
    visibleCount = count
    if visibleCount > 4 then visibleCount = 4
    first = int(m.index/4)*4
    width = 1060 / visibleCount
    for i = first to first+visibleCount-1
        if i < count then UiButton(m.body,m.top.actions[i].title,110+(i-first)*width,425,width-8,56,m.index=i)
    end for
end sub

function onKeyEvent(key as string, press as boolean) as boolean
    if not press then return false
    if key = "back"
        m.top.visible = false
        m.top.selected = "close"
    else if key = "left" or key = "right"
        delta = 1
        if key = "left" then delta = -1
        m.index = WrapIndex(m.index+delta,m.top.actions.count())
        drawActions()
    else if key = "OK"
        if m.top.actions.count() > 0
            m.top.visible = false
            m.top.selected = m.top.actions[m.index].id
        end if
    else if key = "up" or key = "down"
        return true
    else
        return false
    end if
    return true
end function
