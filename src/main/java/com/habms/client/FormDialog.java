package com.habms.client;

import javax.swing.*;
import java.awt.*;

/**
 * A small reusable modal form dialog that hosts a user-provided panel
 * and returns OK/CANCEL. Use like: new FormDialog(parent, "Title", panel).showDialog();
 */
public class FormDialog extends JDialog {
    public static final int OK = 1;
    public static final int CANCEL = 0;
    private int result = CANCEL;

    public FormDialog(Window owner, String title, JPanel content) {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        init(content);
    }

    private void init(JPanel content) {
        JPanel root = new JPanel(new BorderLayout(8,8));
        root.setBorder(BorderFactory.createEmptyBorder(8,8,8,8));
        root.add(content, BorderLayout.CENTER);
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton ok = new JButton("确定");
        JButton cancel = new JButton("取消");
        ok.addActionListener(e -> { result = OK; setVisible(false); });
        cancel.addActionListener(e -> { result = CANCEL; setVisible(false); });
        footer.add(ok); footer.add(cancel);
        root.add(footer, BorderLayout.SOUTH);
        setContentPane(root);
        pack();
        setLocationRelativeTo(getOwner());
    }

    /**
     * Shows the dialog and returns either {@link #OK} or {@link #CANCEL}.
     */
    public int showDialog() {
        result = CANCEL;
        setVisible(true);
        return result;
    }
}
