import SkyprintCore

public func encodeSample() -> Int {
    let doc = ReceiptDocument(paper: PaperWidth.mm80, elements: [
        ElementText(text: "SKYPRINT iOS", style: TextStyle(align: Align.center, bold: true, width: 2, height: 2, inverse: false, underline: false)),
        ElementDivider(char: 45),
        ElementCut(partial: true),
    ])
    let bytes = EscPosEncoder.shared.encode(document: doc, mode: TextMode.ascii, buzzerCommand: BuzzerCommand.escB, rasterizer: nil)
    return Int(bytes.size)
}
