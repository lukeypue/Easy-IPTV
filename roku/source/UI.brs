sub ClearGroup(group as object)
    if group.getChildCount() > 0 then group.removeChildrenIndex(group.getChildCount(), 0)
end sub

function UiBox(parent as object, x as float, y as float, width as float, height as float, color as string) as object
    node = createObject("roSGNode","Rectangle")
    node.translation = [x,y]
    node.width = width
    node.height = height
    node.color = color
    parent.appendChild(node)
    return node
end function

function UiLabel(parent as object, text as string, x as float, y as float, width as float, height as float, size = 20 as integer, color = "0xF2F3F5FF" as string) as object
    node = createObject("roSGNode","Label")
    node.text = text
    node.translation = [x,y]
    node.width = width
    node.height = height
    node.color = color
    node.vertAlign = "center"
    node.font = "font:MediumSystemFont"
    node.font.size = size
    parent.appendChild(node)
    return node
end function

function UiButton(parent as object, text as string, x as float, y as float, width as float, height as float, focused as boolean) as object
    group = createObject("roSGNode","Group")
    group.translation = [x,y]
    parent.appendChild(group)
    border = "0x29445FFF"
    if focused then border = "0xFF2FB9FF"
    UiBox(group,0,0,width,height,border)
    if width > 6 then UiBox(group,3,3,width-6,height-6,"0x16263AFF")
    color = "0xF2F3F5FF"
    if focused then color = "0x49E8FFFF"
    if width > 32
        label = UiLabel(group,text,12,4,width-24,height-8,18,color)
        label.horizAlign = "center"
    end if
    return group
end function

function UiPoster(parent as object, uri as string, x as float, y as float, width as float, height as float) as object
    node = createObject("roSGNode","Poster")
    node.translation = [x,y]
    node.width = width
    node.height = height
    node.loadDisplayMode = "scaleToFit"
    node.uri = uri
    parent.appendChild(node)
    return node
end function

function NowSeconds() as integer
    return createObject("roDateTime").asSeconds()
end function
