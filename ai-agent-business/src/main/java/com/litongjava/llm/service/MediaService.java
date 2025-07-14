package com.litongjava.llm.service;

import com.litongjava.groq.GropModel;
import com.litongjava.groq.GroqSpeechClient;
import com.litongjava.groq.TranscriptionsRequest;
import com.litongjava.groq.TranscriptionsResponse;

public class MediaService {

  public String parseMedia(String filename, byte[] data) {
    String text;
    TranscriptionsRequest transcriptionsRequest = new TranscriptionsRequest();
    transcriptionsRequest.setModel(GropModel.WHISPER_LARGE_V3_TURBO);
    TranscriptionsResponse transcriptions = GroqSpeechClient.transcriptions(data, filename, transcriptionsRequest);
    text = transcriptions.getText();
    return text;
  }

}
