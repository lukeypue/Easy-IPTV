function KeyboardPages() as object
    return [
        ["a","b","c","d","e","f","g","h","i","j","k","l","m","n","o","p","q","r","s","t","u","v","w","x","y","z","0","1","2","3","4","5","6","7","8","9",".","@","_","-"],
        ["A","B","C","D","E","F","G","H","I","J","K","L","M","N","O","P","Q","R","S","T","U","V","W","X","Y","Z","0","1","2","3","4","5","6","7","8","9",".","@","_","-"],
        ["/",":","?","&","=","+","%","#","!","$","(",")","[","]","{","}","<",">","*",chr(92),";",",",chr(34),"'","|","~","^","`","@","-","_",".","0","1","2","3","4","5","6","7"]
    ]
end function

function KeyboardMove(index as integer, key as string, columns as integer, count as integer) as integer
    row = int(index / columns)
    column = index mod columns
    if key = "left" then column = WrapIndex(column-1,columns)
    if key = "right" then column = WrapIndex(column+1,columns)
    if key = "up" then row = WrapIndex(row-1,int(count/columns))
    if key = "down" then row = WrapIndex(row+1,int(count/columns))
    return row*columns+column
end function
