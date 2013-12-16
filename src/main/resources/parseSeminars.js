var events = Sizzle("#content>table:first-of-type>tbody>tr")
var data = []
for (var i = 0; i < events.length; i++) {
	if(Sizzle(">td", events[i]).length > 4) {
		var info = Sizzle(">td:nth-of-type(4)>a:first-of-type", events[i])[0]
		var font = Sizzle("font", info)
		data.push({
			"url": info.href,
			"name": font[0].innerText,
			"description": font[1].innerText
		})
	}
}
return JSON.stringify(data)