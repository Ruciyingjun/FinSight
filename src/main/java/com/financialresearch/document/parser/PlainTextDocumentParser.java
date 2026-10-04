/**
 * 纯文本文档解析器：以严格 UTF-8 解码 TXT、Markdown 和 CSV 文件并生成单个逻辑页。
 */
package com.financialresearch.document.parser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;

@Component
class PlainTextDocumentParser implements DocumentParser {

    private static final Set<String> EXTENSIONS = Set.of(".txt", ".md", ".csv");

    @Override
    public boolean supports(String filename) {
        String normalized = filename.toLowerCase(Locale.ROOT);
        return EXTENSIONS.stream().anyMatch(normalized::endsWith);
    }

    @Override
    public List<ParsedPage> parse(InputStream input) {
        try {
            byte[] bytes = input.readAllBytes();
            String content = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(removeUtf8Bom(bytes)))
                    .toString();
            return List.of(new ParsedPage(1, normalize(content)));
        } catch (IOException exception) {
            throw new DocumentParsingException("文本文件必须使用有效的 UTF-8 编码", exception);
        }
    }

    private byte[] removeUtf8Bom(byte[] bytes) {
        if (bytes.length >= 3 && bytes[0] == (byte) 0xEF
                && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF) {
            return java.util.Arrays.copyOfRange(bytes, 3, bytes.length);
        }
        return bytes;
    }

    private String normalize(String text) {
        return text.replace("\r\n", "\n")
                .replace('\r', '\n')
                .lines()
                .map(String::stripTrailing)
                .collect(java.util.stream.Collectors.joining("\n"))
                .strip();
    }
}
