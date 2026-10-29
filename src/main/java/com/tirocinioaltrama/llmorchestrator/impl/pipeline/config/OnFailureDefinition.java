package com.tirocinioaltrama.llmorchestrator.impl.pipeline.config;

import java.util.Map;

/**
 * Definizione della policy on_failure per uno step di validazione.
 * Supporta:
 * - retry_step: id dello step model_inference antecedente da rieseguire
 * - max_retries: numero massimo di retry SLM (default 1 se omesso, valore <= 0 causa errore)
 * - feedback: se true inietta il feedback guidato dai FieldError strutturati (default true)
 * - escalate_to: opzionale, client e modello verso cui scalare per un singolo tentativo finale
 * - allow_cloud_escalation: flag di autorizzazione per invio a client cloud (default false)
 */
public class OnFailureDefinition {

    private String retryStep;
    private int maxRetries = 1;
    private boolean feedback = true;
    private String escalateTo;

    public OnFailureDefinition() {
    }

    public String getRetryStep() {
        return retryStep;
    }

    public void setRetryStep(String retryStep) {
        this.retryStep = retryStep;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public boolean isFeedback() {
        return feedback;
    }

    public void setFeedback(boolean feedback) {
        this.feedback = feedback;
    }

    public String getEscalateTo() {
        return escalateTo;
    }

    public void setEscalateTo(String escalateTo) {
        this.escalateTo = escalateTo;
    }

    public static OnFailureDefinition fromMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }

        OnFailureDefinition of = new OnFailureDefinition();
        if (map.containsKey("retry_step")) {
            of.setRetryStep(map.get("retry_step") != null ? map.get("retry_step").toString().strip() : null);
        }

        if (map.containsKey("max_retries")) {
            Object mr = map.get("max_retries");
            if (mr instanceof Number num) {
                of.setMaxRetries(num.intValue());
            } else if (mr != null) {
                of.setMaxRetries(Integer.parseInt(mr.toString().strip()));
            }
        } else if (map.containsKey("retry")) {
            Object mr = map.get("retry");
            if (mr instanceof Number num) {
                of.setMaxRetries(num.intValue());
            } else if (mr != null) {
                of.setMaxRetries(Integer.parseInt(mr.toString().strip()));
            }
        }

        if (map.containsKey("feedback")) {
            Object fb = map.get("feedback");
            if (fb instanceof Boolean b) {
                of.setFeedback(b);
            } else if (fb != null) {
                of.setFeedback(Boolean.parseBoolean(fb.toString().strip()));
            }
        }

        if (map.containsKey("escalate_to")) {
            Object esc = map.get("escalate_to");
            if (esc != null) {
                of.setEscalateTo(esc.toString().strip());
            }
        }

        return of;
    }
}
