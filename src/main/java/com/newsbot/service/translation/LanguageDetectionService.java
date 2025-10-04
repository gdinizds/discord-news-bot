package com.newsbot.service.translation;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.langdetect.optimaize.OptimaizeLangDetector;
import org.apache.tika.language.detect.LanguageDetector;
import org.apache.tika.language.detect.LanguageResult;
import org.springframework.stereotype.Service;


@Slf4j
@Service
public class LanguageDetectionService {

    private final LanguageDetector langDetector;

    public LanguageDetectionService() {
        this.langDetector = new OptimaizeLangDetector().loadModels();
        log.info("Detector de idioma inicializado");
    }

    public boolean isPortuguese(String text) {
        if (text == null || text.isBlank()) {
            log.debug("Texto vazio ou nulo - considerado como não-português");
            return false;
        }

        try {
            LanguageResult result = langDetector.detect(text);
            boolean isEnglishWithHighConfidence = result.getLanguage().equals("en") && result.getRawScore() > 0.90;

            if (isEnglishWithHighConfidence) {
                log.debug("Texto detectado como inglês com alta confiança - pontuação: {}", result.getRawScore());
                return false;
            }

            log.debug("Texto aceito como português - idioma detectado: {}, pontuação: {}",
                    result.getLanguage(), result.getRawScore());
            return true;

        } catch (Exception e) {
            log.warn("Erro na detecção de idioma, assumindo como português: {}", e.getMessage());
            return true;
        }
    }
}
