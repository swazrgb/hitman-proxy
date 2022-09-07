package io.hitman;

import com.formdev.flatlaf.FlatLightLaf;
import io.hitman.ui.LoadingForm;
import io.hitman.ui.MainForm;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JFrame;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
@Slf4j
public class HitmanProxyApplication {

  private static JFrame frame;
  private static boolean terminate;
  private static ConfigurableApplicationContext ctx;

  public static void main(String[] args) {
    FlatLightLaf.install();
    initFrame();

    ctx = new SpringApplicationBuilder(HitmanProxyApplication.class)
        .registerShutdownHook(false)
        .run(args);
//    ctx = SpringApplication.run(HitmanProxyApplication.class, args);

//    if (terminate) {
//      ctx.close();
//    } else {
      onStart(ctx.getBean(MainForm.class));
//    }
  }

  public static void initFrame() {
    frame = new JFrame("Hitman 3: Extended Inventory");
    frame.setLocationByPlatform(true);
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
//    frame.addWindowListener(new WindowAdapter() {
//      @Override
//      public void windowClosing(WindowEvent e) {
////        if (terminate) {
////          return;
////        }
////
////        terminate = true;
////        if (ctx != null) {
////          ctx.close();
////        }
//        System.exit(0);
//      }
//    });

    frame.setResizable(false);
    frame.setMinimumSize(new Dimension(400, 100));
    frame.setContentPane(new LoadingForm().getPanel());
    frame.pack();
    frame.setVisible(true);
  }

  public static void onStart(MainForm mainForm) {
    frame.setContentPane(mainForm.getMainPanel());
    frame.setResizable(true);
    frame.setMinimumSize(new Dimension(600, 400));
    frame.pack();
    frame.setVisible(true);
  }
}
