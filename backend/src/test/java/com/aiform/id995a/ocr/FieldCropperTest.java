package com.aiform.id995a.ocr;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class FieldCropperTest {

  @Test
  void excludesDiagonallyAdjacentLabelFromPaddedJudgeCrop() throws Exception {
    RenderedOcrPage page = renderedPage();

    FieldCropper.CropResult crop = FieldCropper.crop(
        page,
        List.of(82, 20, 180, 40),
        FieldCropper.CropKind.SNAPSHOT,
        List.of(60, 0, 82, 18)
    );

    assertThat(crop.bbox()).containsExactly(72, 18, 194, 46);
  }

  private RenderedOcrPage renderedPage() throws Exception {
    BufferedImage image = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = image.createGraphics();
    graphics.setColor(Color.WHITE);
    graphics.fillRect(0, 0, 200, 200);
    graphics.dispose();
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ImageIO.write(image, "png", output);
    return new RenderedOcrPage(1, output.toByteArray(), "", 200, 200);
  }
}
