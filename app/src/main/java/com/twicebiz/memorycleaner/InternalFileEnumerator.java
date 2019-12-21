package com.twicebiz.memorycleaner;

import android.content.Context;
import android.os.Environment;
import android.os.storage.StorageManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import android.os.StatFs;
import android.util.Log;

import java.nio.channels.FileChannel;
import java.util.Date;

public class InternalFileEnumerator {
    private long LENGTH_TO_CLEAN = 0;
    private long COUNT_TO_CLEAN = 0;
    private String SDPATH = "";

    public long getLastCountToClean() {
        return COUNT_TO_CLEAN;
    }

    private String[] cleaningPaths = {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath() + "/Camera/",
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getAbsolutePath(),
            Environment.getExternalStorageDirectory().getAbsolutePath() + "/WhatsApp/Media/WhatsApp Images",
            Environment.getExternalStorageDirectory().getAbsolutePath() + "/WhatsApp/Media/WhatsApp Video",
            Environment.getExternalStorageDirectory().getAbsolutePath() + "/WhatsApp/WhatsApp Images",
            Environment.getExternalStorageDirectory().getAbsolutePath() + "/WhatsApp/WhatsApp Video"
    };
    private String[] warningPaths = {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath(),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).getAbsolutePath(),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).getAbsolutePath(),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PODCASTS).getAbsolutePath(),
            Environment.getExternalStorageDirectory().getAbsolutePath() + "/WhatsApp/"
    };

    public InternalFileEnumerator() {

    }

    protected boolean safeMoveFile(File src, File dst) {
        FileChannel inChannel = null;
        FileChannel outChannel = null;
        boolean result = false;
        try {
            // TODO set the origin last usage date on the new file?
            // TODO add the delete action
            Log.d("safeMoveFile", src.getAbsolutePath() +" > "+ dst.getAbsolutePath());
            if (!dst.exists()) {
                boolean b = dst.mkdirs();
                if (!b) Log.d("safeMoveFile", "Cannot create a dir!!! " + dst.getAbsolutePath());
            }
            File dstFile = new File(getUniqueFile(dst.getAbsolutePath()+"/"+src.getName()));
            if (dstFile.createNewFile()) {
                inChannel = new FileInputStream(src).getChannel();
                outChannel = new FileOutputStream(dstFile).getChannel();
                inChannel.transferTo(0, inChannel.size(), outChannel);
                result = true;
            }
        }
        catch (Exception e) {
            result = false;
            e.printStackTrace();
        }
        finally
        {
            if (inChannel != null) {
                try {
                    inChannel.close();
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }

            if (outChannel != null) {
                try {
                    outChannel.close();
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return result;
    } // safeMoveFile

    private String getUniqueFile(String s) {
        boolean unique = false;
        int counter = 1;
        int splitindex = s.lastIndexOf(".");
        if (splitindex<1) splitindex = s.length()-1;
        String suffix = s.substring(splitindex);
        while (!unique) {
            File f = new File(s);
            if (!f.exists()) unique = true;
            else {
                s = s.substring(0,splitindex-1) + "_" + counter++ + suffix;
            }
        }
        return s;
    }

    /**
     * Get external sd card path using reflection - manage by is_removable variable if is external storage removable = SD card
     */
    private static String getExternalStoragePath(Context mContext, boolean is_removable) {

        StorageManager mStorageManager = (StorageManager) mContext.getSystemService(Context.STORAGE_SERVICE);
        Class<?> storageVolumeClazz = null;
        try {
            storageVolumeClazz = Class.forName("android.os.storage.StorageVolume");
            Method getVolumeList = mStorageManager.getClass().getMethod("getVolumeList");
            Method getPath = storageVolumeClazz.getMethod("getPath");
            Method isRemovable = storageVolumeClazz.getMethod("isRemovable");
            Object result = getVolumeList.invoke(mStorageManager);
            final int length = Array.getLength(result);
            for (int i = 0; i < length; i++) {
                Object storageVolumeElement = Array.get(result, i);
                String path = (String) getPath.invoke(storageVolumeElement);
                boolean removable = (Boolean) isRemovable.invoke(storageVolumeElement);
                if (is_removable == removable) {
                    return path;
                }
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
        return null;
    } // getExternalStoragePath

    public boolean isMediaAvailable() {
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            return true;
        } else {
            return false;
        }
    } // isMediaAvailable

    private String s_scanDirectoryFiles(File path, int days, boolean bMove) {
        long length = 0;
        long lengthToMove = 0;
        int countToClean = 0;
        int count = 0;

        String ret = "";
        if (path == null) return "";
        if (path.isFile()) return sizeFormat(path.length());
        if (path.listFiles() == null) return "";
        if (bMove && (SDPATH==null || SDPATH.length()==0)) return "ERROR SD-CARD PATH";

        for (File file : path.listFiles()) {
            if (file.isFile() && !skipFile(file)) {
                long len = file.length();
                length += len;
                count++;
                boolean older = isFileOlderThenDays(file, days);
                if (older) {
                    lengthToMove += len;
                    countToClean++;
                    if (bMove) {
                        // TODO must generate the proper save directory path!
                        if (!safeMoveFile(file, new File(SDPATH+"/NEW_SD"))) {
                            ret = ret + "- ERROR COPY " + file.getName() + "\n";
                        } else {
                            lengthToMove = lengthToMove - len;
                            countToClean--;
                        }
                    }
                    //ret = ret + file.getName() + ": " + sizeFormat(len) + "\n";
                }
                //ret = ret + file.getName() + ": " + sizeFormat(len) + " older: " + older + "\n";
            }
            // NEJSME REKURZIVNI, TAKZE KDYZ DIR, TAK NEZANORUJEME!!!
        }

        if (count == 0) return "empty";

        LENGTH_TO_CLEAN += lengthToMove;
        COUNT_TO_CLEAN += countToClean;

        return " " + sizeFormat(lengthToMove) + " / " + sizeFormat(length) + " (" + countToClean + "/" + count + ")" + "\n" + ret;
    } // s_scanDirectoryFiles

    private boolean skipFile(File file) {
        if (file.getName().startsWith(".")) return true;
        if (file.length() == 0) return true;

        return false;
    } // skipFile

    private String sizeFormat(long size) {
         long kb = size / 1024; // Get size and convert bytes into Kb.
          if (kb >= 1024) {
                return (kb / 1024) + " MB";
          }
          return kb + " KB";
    } // sizeFormat

    private boolean isFileOlderThenDays(File file, long days) {
        if (days == 0) return true;

        long diff = new Date().getTime() - file.lastModified();

        if (diff > days * 24 * 60 * 60 * 1000) return true;
        return false;
    } // isFileOlderThenDays

    public boolean externalMemoryAvailableForWrite() {
        if (Environment.isExternalStorageRemovable()) {
            //device support sd card. We need to check sd card availability.
            String state = Environment.getExternalStorageState();
            return state.equals(Environment.MEDIA_MOUNTED);
        } else {
            //device not support sd card or is read only
            return false;
        }
    } // externalMemoryAvailableForWrite

    /*
     * Interni uloziste/WhatsApp/WhatsApp Images (pozor, obsahuje i podslozky, co nech byt)
     * Interni uloziste/WhatsApp/WhatsApp Images/Sent - promazat
     * Interni uloziste/WhatsApp/WhatsApp Video
     * Interni uloziste/WhatsApp/WhatsApp Video/Sent - promazat
     * Interni uloziste/Pictures (pozor, obsahuje podslozky, co nech byt)
     * Interni uloziste/DCIM/Camera
     *
     * Vsechny WhatsApp veci se presouvaji do SDKarta/WhatsApp
     * Vsechny Pictures se presouvaji do SDKarta/Pictures
     * Vsechny Fotky se presouvaji do SDKarta/DCIM/Camera
     *
     * Bacha na konflikty se jmeny, ktere se asi mohou vyskytnout, kdyz jsou predchozi soubory presunuty
     **/

    // https://stackoverflow.com/questions/30281890/list-the-files-in-download-directory-of-the-android-phone/35862041
    public String scanMemoryForOld(Context context, int days, boolean bMove) {
        String sReturn = "";
        LENGTH_TO_CLEAN = 0;
        COUNT_TO_CLEAN = 0;

        if (!isMediaAvailable()) {
            return "ERROR: ACCESS MEMORY ERROR!";
        }

        String sdPath = getExternalStoragePath(context, true);
        if (/*!externalMemoryAvailableForWrite() || */sdPath == null) {
            return "ERROR: SD-CARD NOT FOUND OR READ ONLY!";
        } else {
            SDPATH = sdPath;
            // TODO for debugging use the internal DOCUMENTS directory, for release use SDPATH
            //SDPATH = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).getAbsolutePath();
        }

        if (days>0)
            sReturn = sReturn + "Keeping files used in " + days + " days...\n\n";
        else
            sReturn = sReturn + "Warning! Not recommended to used with keeping zero days set!\n\n";

        if (bMove) sReturn = sReturn + "|| Cleaned: ";
        else sReturn = sReturn + "|| Found to clean: ";
        String tempReturn = "";
        for (String path: cleaningPaths) {
            File dirToClean = new File(path);
            if (dirToClean == null) continue;
            if (!dirToClean.exists()) {
                if (!bMove)
                    tempReturn = tempReturn + "\n- " + getNiceName(path) + " not found";
            }
            else {
                tempReturn = tempReturn + "\n- " + getNiceName(path) + " " + s_scanDirectoryFiles(dirToClean, days, bMove);
            }
        }

        sReturn = sReturn + sizeFormat(LENGTH_TO_CLEAN) + " / " + COUNT_TO_CLEAN + " items total \n";
        sReturn = sReturn + tempReturn;

        if (!bMove) {
            // zazalohujeme, protoze to bude ovlivneno analyzou cest, co se nebudou mazat
            long LENGTH_TO_CLEAN_orig = LENGTH_TO_CLEAN;
            long COUNT_TO_CLEAN_orig = COUNT_TO_CLEAN;

            sReturn = sReturn + "\n\n|| Other analysis - ONLY FOR INFO!\n";
            for (String path: warningPaths) {
                File dirToClean = new File(path);
                if (dirToClean == null) continue;
                if (!dirToClean.exists()) sReturn = sReturn + "\n- " + getNiceName(path) + " not found";
                else sReturn = sReturn + "\n- " + getNiceName(path) + " " + s_scanDirectoryFiles(dirToClean, days, false);
            }

            // obnovime, chceme dale pracovat jen s tim, co budeme fakt mazat
            LENGTH_TO_CLEAN = LENGTH_TO_CLEAN_orig;
            COUNT_TO_CLEAN = COUNT_TO_CLEAN_orig;
        }

        // https://stackoverflow.com/questions/3394765/how-to-check-available-space-on-android-device-on-sd-card
        long freeCard = getSDCardFreeSpace(sdPath);
        sReturn = sReturn + "\n\nSD-CARD found! Path = " + sdPath + "\n| free space " + sizeFormat(freeCard) + "\n";

        return sReturn;
    } // scanMemoryForOld

    // https://stackoverflow.com/questions/3394765/how-to-check-available-space-on-android-device-on-sd-card
    private long getSDCardFreeSpace(String path) {
        // StatFs stat_fs = new StatFs(Environment.getExternalStorageDirectory().getPath());
        StatFs stat_fs = new StatFs(path);
        double avail_sd_space = (double)stat_fs.getAvailableBlocksLong() *(double)stat_fs.getBlockSizeLong();
        //double Kb_Available = (avail_sd_space / 1024);
        return (long)(avail_sd_space);
    } // getSDCardFreeSpace

    private String getNiceName(String path) {
        String sub = Environment.getExternalStorageDirectory().getAbsolutePath();
        if (path.startsWith(sub)) return path.substring(sub.length()+1);
        return path;
    } // getNiceName
}
