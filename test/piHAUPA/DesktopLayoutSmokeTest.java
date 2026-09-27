package piHAUPA;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Kiểm tra kích thước điều khiển ở cột trái mà không cần chụp màn hình. */
public final class DesktopLayoutSmokeTest {
    private DesktopLayoutSmokeTest() { }

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JFrame frame = null;
            try {
                Constructor<DesktopApp> constructor = DesktopApp.class.getDeclaredConstructor();
                constructor.setAccessible(true);
                frame = constructor.newInstance();
                Method selectInput = DesktopApp.class.getDeclaredMethod("selectInput", Path.class);
                selectInput.setAccessible(true);
                selectInput.invoke(frame, Path.of("examples", "paper-scaled-9000.txt").toAbsolutePath());
                frame.validate();
                JButton output = findButton(frame, "Thư mục xuất báo cáo");
                JButton export = findButton(frame, "Xuất TXT và CSV");
                JButton run = findButton(frame, "Chạy và so sánh");
                require(output.getWidth() >= 250 && output.getHeight() >= 40,
                        "Nút xuất báo cáo bị co: " + output.getBounds());
                require(run.getWidth() >= 250 && run.getHeight() >= 40,
                        "Nút chạy bị co: " + run.getBounds());
                require(export.getWidth() >= 250 && export.getHeight() >= 40,
                        "Nút TXT/CSV bị co: " + export.getBounds());
                Rectangle bounds = SwingUtilities.convertRectangle(
                        output.getParent(), output.getBounds(), frame.getContentPane());
                require(bounds.x >= 0 && bounds.x + bounds.width < 400,
                        "Nút xuất tràn khỏi cột trái: " + bounds);
                JLabel title = findLabels(frame).stream()
                        .filter(label -> label.getText().equals("Cấu hình phân tích"))
                        .findFirst().orElseThrow();
                Rectangle titleBounds = SwingUtilities.convertRectangle(
                        title.getParent(), title.getBounds(), frame.getContentPane());
                require(titleBounds.y < 160, "Nội dung cột trái bị canh giữa: " + titleBounds);
                boolean checkedInputLabel = false;
                for (JLabel label : findLabels(frame)) {
                    if (label.getText().startsWith("TXT · Tệp dữ liệu")) {
                        require(label.getWidth() <= 270, "Nhãn nguồn dữ liệu tràn: " + label.getBounds());
                        checkedInputLabel = true;
                    }
                }
                require(checkedInputLabel, "Không tìm thấy nhãn dữ liệu đã chọn.");
                System.out.println("Desktop left layout OK: run=" + run.getBounds()
                        + ", output=" + output.getBounds() + ", export=" + export.getBounds());
            } catch (Exception error) {
                throw new RuntimeException(error);
            } finally {
                if (frame != null) frame.dispose();
            }
        });
    }

    private static JButton findButton(Container root, String text) {
        for (Component child : root.getComponents()) {
            if (child instanceof JButton button && button.getText().contains(text)) return button;
            if (child instanceof Container container) {
                JButton found = findButtonOrNull(container, text);
                if (found != null) return found;
            }
        }
        throw new AssertionError("Không tìm thấy nút: " + text);
    }

    private static JButton findButtonOrNull(Container root, String text) {
        for (Component child : root.getComponents()) {
            if (child instanceof JButton button && button.getText().contains(text)) return button;
            if (child instanceof Container container) {
                JButton found = findButtonOrNull(container, text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static List<JLabel> findLabels(Container root) {
        List<JLabel> labels = new ArrayList<>();
        for (Component child : root.getComponents()) {
            if (child instanceof JLabel label) labels.add(label);
            if (child instanceof Container container) labels.addAll(findLabels(container));
        }
        return labels;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
