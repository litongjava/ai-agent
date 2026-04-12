package nexus.io.llm.service;

import nexus.io.groq.GropModel;
import nexus.io.groq.GroqSpeechClient;
import nexus.io.groq.TranscriptionsRequest;
import nexus.io.groq.TranscriptionsResponse;

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
