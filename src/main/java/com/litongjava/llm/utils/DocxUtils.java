package com.litongjava.llm.utils;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

public class DocxUtils {

  public static String parseDocx(byte[] fileData) throws IOException {
    StringBuilder content = new StringBuilder();
    try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(fileData))) {
      // 读取段落
      for (XWPFParagraph paragraph : document.getParagraphs()) {
        content.append(paragraph.getText()).append("\n");
      }

      // 读取表格
      for (XWPFTable table : document.getTables()) {
        for (XWPFTableRow row : table.getRows()) {
          for (XWPFTableCell cell : row.getTableCells()) {
            content.append(cell.getText()).append("\t");
          }
          content.append("\n");
        }
      }

    }
    return content.toString().trim();
  }
}
