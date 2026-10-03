package com.example.attendanceapplication.utils;

import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.BulletSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dựng Markdown đơn giản thành văn bản có định dạng để hiển thị trong bong bóng chat.
 *
 * Mô hình AI trả lời có Markdown ({@code **đậm**}, gạch đầu dòng, tiêu đề). Nếu đưa
 * thẳng vào TextView thì người dùng nhìn thấy nguyên ký tự sao — đó là lỗi cần chữa.
 *
 * Chỉ xử lý những thứ mô hình thực sự hay dùng, cố tình KHÔNG kéo thêm thư viện:
 * đậm, nghiêng, mã inline, gạch đầu dòng, danh sách đánh số, tiêu đề.
 */
public final class MarkdownRenderer {

    private MarkdownRenderer() { }

    private static final Pattern BOLD   = Pattern.compile("\\*\\*(?=\\S)(.+?)(?<=\\S)\\*\\*", Pattern.DOTALL);
    private static final Pattern ITALIC = Pattern.compile("(?<![*\\w])\\*(?=\\S)([^*\\n]+?)(?<=\\S)\\*(?![*\\w])");
    private static final Pattern CODE   = Pattern.compile("`([^`\\n]+)`");

    private static final Pattern HEADING = Pattern.compile("^\\s{0,3}#{1,6}\\s+(.*)$");
    private static final Pattern BULLET  = Pattern.compile("^\\s{0,6}[-*+•]\\s+(.*)$");
    private static final Pattern NUMBER  = Pattern.compile("^\\s{0,6}(\\d{1,2})[.)]\\s+(.*)$");

    private static final int BULLET_GAP = 22;

    /** Trả về chuỗi đã gắn định dạng, sẵn sàng đưa vào {@code TextView.setText()}. */
    public static CharSequence render(String markdown) {
        if (markdown == null || markdown.isEmpty()) return "";

        String[] lines = markdown.replace("\r\n", "\n").replace("\r", "\n").split("\n", -1);

        SpannableStringBuilder out = new SpannableStringBuilder();
        // Ghi lại vị trí các dòng cần gắn span ở mức khối, xử lý sau khi ghép xong
        java.util.List<int[]> headings = new java.util.ArrayList<>();
        java.util.List<int[]> bullets  = new java.util.ArrayList<>();

        int blankRun = 0;
        for (String raw : lines) {
            String line = stripTrailing(raw);

            if (line.trim().isEmpty()) {
                blankRun++;
                // gộp nhiều dòng trống liên tiếp thành một
                if (blankRun <= 1 && out.length() > 0) out.append('\n');
                continue;
            }
            blankRun = 0;
            if (out.length() > 0) out.append('\n');

            Matcher mh = HEADING.matcher(line);
            if (mh.matches()) {
                int start = out.length();
                out.append(mh.group(1).trim());
                headings.add(new int[]{start, out.length()});
                continue;
            }

            Matcher mb = BULLET.matcher(line);
            if (mb.matches()) {
                int start = out.length();
                out.append(mb.group(1).trim());
                bullets.add(new int[]{start, out.length()});
                continue;
            }

            Matcher mn = NUMBER.matcher(line);
            if (mn.matches()) {
                int start = out.length();
                out.append(mn.group(1)).append(". ").append(mn.group(2).trim());
                // danh sách đánh số chỉ cần thụt lề, không cần chấm tròn
                out.setSpan(new LeadingMarginSpan.Standard(0, BULLET_GAP),
                        start, out.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                continue;
            }

            out.append(line.trim());
        }

        // Gắn span mức khối TRƯỚC, vì applyInline xoá bớt ký tự đánh dấu và làm
        // chuỗi ngắn lại. SpannableStringBuilder tự dời span đã gắn theo, còn
        // toạ độ ghi sẵn trong headings/bullets thì không — gắn sau sẽ lệch chỗ.
        for (int[] r : headings) {
            if (r[0] >= r[1] || r[1] > out.length()) continue;
            out.setSpan(new StyleSpan(Typeface.BOLD), r[0], r[1], Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            out.setSpan(new RelativeSizeSpan(1.08f), r[0], r[1], Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        for (int[] r : bullets) {
            if (r[0] >= r[1] || r[1] > out.length()) continue;
            out.setSpan(new BulletSpan(BULLET_GAP), r[0], r[1], Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        // Định dạng trong dòng
        applyInline(out, BOLD,   () -> new StyleSpan(Typeface.BOLD));
        applyInline(out, CODE,   () -> new TypefaceSpan("monospace"));
        applyInline(out, ITALIC, () -> new StyleSpan(Typeface.ITALIC));

        return trimEnd(out);
    }

    /**
     * Gỡ bỏ ký tự đánh dấu Markdown, trả về văn bản thuần một dòng.
     *
     * Dùng cho dòng xem trước trong danh sách lịch sử: ở đó không gắn span được
     * vì chuỗi còn bị cắt ngắn, mà để nguyên thì người dùng thấy đầy dấu sao.
     */
    public static String plain(String markdown) {
        if (markdown == null || markdown.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        for (String raw : markdown.replace("\r\n", "\n").replace("\r", "\n").split("\n", -1)) {
            String line = raw.trim();
            if (line.isEmpty()) continue;

            Matcher mh = HEADING.matcher(line);
            Matcher mb = BULLET.matcher(line);
            Matcher mn = NUMBER.matcher(line);
            if (mh.matches()) {
                line = mh.group(1).trim();
            } else if (mb.matches()) {
                line = mb.group(1).trim();
            } else if (mn.matches()) {
                line = mn.group(1) + ". " + mn.group(2).trim();
            }

            if (sb.length() > 0) sb.append(' ');
            sb.append(line);
        }

        // BOLD phải chạy trước ITALIC, vì ** cũng khớp một phần với mẫu *
        String out = sb.toString();
        out = BOLD.matcher(out).replaceAll("$1");
        out = CODE.matcher(out).replaceAll("$1");
        out = ITALIC.matcher(out).replaceAll("$1");
        return out.trim();
    }

    private interface SpanFactory {
        Object create();
    }

    /**
     * Xoá cặp ký tự đánh dấu và gắn span vào phần nội dung bên trong.
     * Quét tiến dần để không lặp vô hạn khi nội dung vẫn còn ký tự giống dấu.
     */
    private static void applyInline(SpannableStringBuilder sb, Pattern p, SpanFactory factory) {
        int from = 0;
        int guard = 0;
        while (from < sb.length() && guard++ < 500) {
            Matcher m = p.matcher(sb);
            if (!m.find(from)) return;

            int start = m.start();
            String inner = m.group(1);
            sb.replace(start, m.end(), inner);
            sb.setSpan(factory.create(), start, start + inner.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            from = start + inner.length();
        }
    }

    private static String stripTrailing(String s) {
        int end = s.length();
        while (end > 0 && (s.charAt(end - 1) == ' ' || s.charAt(end - 1) == '\t')) end--;
        return s.substring(0, end);
    }

    private static CharSequence trimEnd(SpannableStringBuilder sb) {
        int end = sb.length();
        while (end > 0 && Character.isWhitespace(sb.charAt(end - 1))) end--;
        if (end < sb.length()) sb.delete(end, sb.length());
        return sb;
    }
}
