import Skyprint

public func encodeSample() -> Int {
    let doc = ReceiptDocument(paper: PaperWidth.mm80, elements: [
        ElementText(text: "SKYPRINT iOS", style: TextStyle(align: Align.center, bold: true, width: 2, height: 2, inverse: false, underline: false)),
        ElementDivider(char: 45),
        ElementCut(partial: true),
    ])
    let bytes = EscPosEncoder.shared.encode(document: doc, mode: TextMode.ascii, buzzerCommand: BuzzerCommand.escB, rasterizer: nil)
    return Int(bytes.size)
}

/// Render mẫu JSON (skyprint-template) -> ReceiptDocument -> ESC/POS. Trả về số phần tử đã render.
public func renderTemplateSample() -> Int {
    let templateJson = """
    {"id":"t","version":1,"document":"RECEIPT","paper":"MM80","elements":[
      {"type":"text","value":"{{store.name}}","style":{"align":"CENTER","bold":true}},
      {"type":"group","each":"items","as":"it","elements":[
        {"type":"row","columns":[{"text":"{{it.name}}","weight":6},{"text":"{{it.amount | money}}","weight":3,"align":"RIGHT"}]}]},
      {"type":"cut"}]}
    """
    let dataJson = #"{"store":{"name":"QUAN A"},"items":[{"name":"Pho bo","amount":50000},{"name":"Tra da","amount":5000}]}"#
    let template = TemplateParser.shared.parse(json: templateJson)
    let data = TemplateValueCompanion.shared.fromJson(json: dataJson)
    let options = RenderOptions(money: MoneyFormat.companion.VND, paper: nil, images: nil)
    let result = TemplateEngine.shared.render(template: template, data: data, options: options)
    return result.document.elements.count
}
