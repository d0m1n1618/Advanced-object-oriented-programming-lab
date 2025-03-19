package com.lab.statistics;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class MainFrame {
    private JFrame frame;
    private final StatisticsService statisticsService;

    public MainFrame() { //Lokalizacja katalogu
        statisticsService = new StatisticsService("C:\\Users\\Administrator\\IdeaProjects\\StatystykaWyrazow\\src", 1, 2, 10);
        initialize();
    }

    //Inicjalizacja UI
    private void initialize() {
        frame = new JFrame();
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                statisticsService.shutdown();
            }
        });
        frame.setBounds(100, 100, 450, 100);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel panel = new JPanel();
        frame.getContentPane().add(panel, BorderLayout.NORTH);

        JButton btnStart = new JButton("Start");
        btnStart.addActionListener(e -> statisticsService.startStatistics());

        JButton btnStop = new JButton("Stop");
        btnStop.addActionListener(e -> statisticsService.stopStatistics());

        JButton btnZamknij = new JButton("Zamknij");
        btnZamknij.addActionListener(e -> {
            statisticsService.shutdown();
            System.exit(0);
        });

        panel.add(btnStart);
        panel.add(btnStop);
        panel.add(btnZamknij);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            MainFrame window = new MainFrame();
            window.frame.setVisible(true);
        });
    }
}
