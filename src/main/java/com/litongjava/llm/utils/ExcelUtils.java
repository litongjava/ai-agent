package com.litongjava.llm.utils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Sheet;

public class ExcelUtils {

  /**
   * 从xlsx字节数据中提取文本内容
   * @param fileData xlsx文件的字节数组
   * @return 提取的文本内容
   * @throws IOException 如果xlsx解析失败
   */
  public static String parseXlsx(byte[] fileData) throws IOException {
    StringBuilder content = new StringBuilder();
    try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(fileData))) {
      int numberOfSheets = workbook.getNumberOfSheets();
      for (int i = 0; i < numberOfSheets; i++) {
        Sheet sheet = workbook.getSheetAt(i);
        content.append("Sheet: ").append(sheet.getSheetName()).append("\n");
        for (Row row : sheet) {
          for (Cell cell : row) {
            content.append(cell.toString()).append("\t");
          }
          content.append("\n");
        }
        content.append("----- End of Sheet -----\n");
      }
    }
    return content.toString().trim();
  }
}
