package com.litongjava.llm.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.litongjava.groq.GropConst;
import com.litongjava.jfinal.aop.Aop;
import com.litongjava.llm.utils.DocxUtils;
import com.litongjava.llm.utils.ExcelUtils;
import com.litongjava.llm.utils.PdfUtils;
import com.litongjava.llm.utils.PptxUtils;
import com.litongjava.tio.http.common.UploadFile;
import com.litongjava.tio.utils.hutool.FilenameUtils;

public class ChatFileService {

  public String parseFile(UploadFile uploadFile) throws IOException {
    String name = uploadFile.getName();
    byte[] data = uploadFile.getData();
    String suffix = FilenameUtils.getSuffix(name).toLowerCase();
    String text = null;

    if ("txt".equals(suffix) || "md".equals(suffix)) {
      text = new String(data, StandardCharsets.UTF_8);
      
    } else if (GropConst.SUPPORT_LIST.contains(suffix)) {
      MediaService mediaService = Aop.get(MediaService.class);
      text = mediaService.parseMedia(name, data);
      
    } else if ("pdf".equals(suffix)) {
      text = PdfUtils.parseContent(data);
    } else if ("docx".equals(suffix)) {
      text = DocxUtils.parseDocx(data);
    } else if ("xlsx".equals(suffix)) {
      text = ExcelUtils.parseXlsx(data);
    } else if ("pptx".equals(suffix)) {
      text = PptxUtils.parsePptx(data);
    } else if ("jpg".equals(suffix) || "jpeg".equals(suffix) || "png".equals(suffix)) {
      text = Aop.get(LlmOcrService.class).parse(data, name);
    }
    return text;
  }
}
