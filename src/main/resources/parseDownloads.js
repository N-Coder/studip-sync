var rows = Sizzle("#content>table>tbody>tr:nth-of-type(2)>td:nth-of-type(2)>table>tbody>tr>td>table")
var data = []
for (var i = 0; i < rows.length; i++) {
    var content = Sizzle(">tbody>tr>td.printhead", rows[i])
    var insets = Sizzle(">tbody>tr>td.blank img[src=\"https://studip.uni-passau.de/studip/pictures/forumleer.gif\"]", rows[i])
    if (content.length > 0) {
        var info = Sizzle("a", content[1])
        var link = Sizzle("span a", content[2])
        var time = Sizzle("span a~span", content[2])
        if (info.length > 0 && link.length > 0 && time.length > 0) {
            data.push({
                "displayName": info[0].innerText,
                "url": link[0].href,
                "lastModified": time[0].innerText.trim(),
                "level": insets.length - 2
            })
            //TODO read size, description
        }
    }
}
JSON.stringify(data);
