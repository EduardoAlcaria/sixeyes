package com.sixeyes.controller;

import org.libtorrent4j.*;

import org.libtorrent4j.AlertListener;

import org.libtorrent4j.SessionManager;
import org.libtorrent4j.TorrentInfo;
import org.libtorrent4j.alerts.*;


import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;


public class TorrentDownloader {

    public static void downloader(String magnet) throws InterruptedException, RuntimeException, IOException{



        final SessionManager s = new SessionManager();

        final CountDownLatch signal = new CountDownLatch(1);


        AlertListener l = new AlertListener() {
            int grade = 0;
            @Override
            public int[] types() {
                return null;
            }

            @Override
            public void alert(Alert<?> alert) {

                switch (alert.type()) {
                    case ADD_TORRENT:
                        ((AddTorrentAlert) alert).handle().resume();

                    case PIECE_FINISHED:
                        int progress = (int) (((PieceFinishedAlert) alert).handle().status().progress() * 100);
                        if (grade < progress / 20){
                            int pieceIndex = (int) (((PieceFinishedAlert) alert).pieceIndex());
                            s.downloadRate();

                        }
                        break;

                    case TORRENT_ERROR:
                        System.out.println(((TorrentErrorAlert) alert).what());

                    case BLOCK_FINISHED:

                        assert alert instanceof BlockFinishedAlert;
                        progress = (int) (((BlockFinishedAlert) alert).handle().status().progress() * 100);
                        if (grade < progress / 20){
                            int index = (int) (((BlockFinishedAlert) alert).blockIndex());
                            grade += 1;
                            s.downloadRate();
                        }
                        break;

                    case TORRENT_FINISHED:
                        grade = 0;
                        ((TorrentFinishedAlert) alert).handle().pause();
                        break;
                }
            }
        };

        s.addListener(l);
        s.start();

        final Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                long nodes = s.stats().dhtNodes();
                if (nodes >= 10) {
                    System.out.println("DHT contains " + nodes + " nodes");
                    signal.countDown();
                    timer.cancel();
                }
            }
        }, 0, 1000);

        boolean r = signal.await(10, TimeUnit.SECONDS);
        if (!r) {
            System.out.println("DHT bootstrap timeout");
            System.exit(0);
        }


        byte[] data = s.fetchMagnet(magnet, 30, new File("/tmp"));
        if (data == null) {
            System.out.println("data == null");
            s.stop();
            return;
        }

        TorrentInfo tInfo = TorrentInfo.bdecode(data);

        File f = new File("test.torrent");
        FileOutputStream fos = new FileOutputStream(f);
        fos.write(data);
        File save = new File("java/controller/downloads");

        s.download(new TorrentInfo(f), save);

        int i = 0;
        while (i < 20){
            TorrentStatus.State state = s.find(tInfo.infoHash()).status().state();
            String name = s.find(tInfo.infoHash()).getName();
            if (state == TorrentStatus.State.SEEDING){
                System.out.println(name + "Torrent Finished");
                break;
            }
            TimeUnit.SECONDS.sleep(1);
            System.out.println(state + " State");
            System.out.println(s.find(tInfo.infoHash()).status().progress() * 100 + "% Progress");
        }


    }

}
