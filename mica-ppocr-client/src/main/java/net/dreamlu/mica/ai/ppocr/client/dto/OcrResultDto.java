package net.dreamlu.mica.ai.ppocr.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 通用文本识别结果 DTO (JDK 8 兼容)
 *
 * @author Antigravity
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OcrResultDto {

    /**
     * 识别出的文本内容
     */
    private String text;

    /**
     * 识别置信度 (0.0 ~ 1.0)
     */
    private float score;

    /**
     * 文本框的四角多边形坐标，格式 [[x1,y1], [x2,y2], [x3,y3], [x4,y4]]
     */
    private int[][] box;

    /**
     * 文本框顺时针旋转角度 (0/90/180/270)
     */
    private int rotatedDegrees;

    public OcrResultDto() {
    }

    public OcrResultDto(String text, float score, int[][] box, int rotatedDegrees) {
        this.text = text;
        this.score = score;
        this.box = box;
        this.rotatedDegrees = rotatedDegrees;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public float getScore() {
        return score;
    }

    public void setScore(float score) {
        this.score = score;
    }

    public int[][] getBox() {
        return box;
    }

    public void setBox(int[][] box) {
        this.box = box;
    }

    public int getRotatedDegrees() {
        return rotatedDegrees;
    }

    public void setRotatedDegrees(int rotatedDegrees) {
        this.rotatedDegrees = rotatedDegrees;
    }

    @Override
    public String toString() {
        return "OcrResultDto{" +
                "text='" + text + '\'' +
                ", score=" + score +
                '}';
    }
}
