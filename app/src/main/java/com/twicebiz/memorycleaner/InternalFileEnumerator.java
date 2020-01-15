package com.twicebiz.memorycleaner;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.os.storage.StorageManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import android.os.StatFs;
import android.provider.DocumentsContract;
import android.support.v4.provider.DocumentFile;
import android.webkit.MimeTypeMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;

public class InternalFileEnumerator {
    private long LENGTH_TO_CLEAN = 0;
    private long COUNT_TO_CLEAN = 0;
    public String SDPATH = "";
    private String sRetTrace = "";
    private Boolean TESTNODELETE = false;
    public String copyTestFolder = "MEMCLEANER_COPY_TEMP";
    public Boolean bLastrunSuccess = false;

    public long getLastCountToClean() {
        return COUNT_TO_CLEAN;
    }

    public class FileQueue {
        public String sDestPath;
        ArrayList<File> fileList = new ArrayList<File>();
    }

    private ArrayList<FileQueue> fileResult = new ArrayList<FileQueue>();

    private class SourceDestPath {
        String destFolder;
        String sourcePath;
        SourceDestPath(String d, String s) {destFolder = d; sourcePath = s;}
    } // SourceDestPath

    private SourceDestPath[] cleaningPaths = {
            new SourceDestPath("Camera", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath() + "/Camera/"),
            new SourceDestPath("Pictures", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getAbsolutePath()+""),
            new SourceDestPath("WhatsApp", Environment.getExternalStorageDirectory().getAbsolutePath() + "/WhatsApp/Media/WhatsApp Images"),
            new SourceDestPath("WhatsApp", Environment.getExternalStorageDirectory().getAbsolutePath() + "/WhatsApp/Media/WhatsApp Video"),
            new SourceDestPath("WhatsApp", Environment.getExternalStorageDirectory().getAbsolutePath() + "/WhatsApp/WhatsApp Images"),
            new SourceDestPath("WhatsApp", Environment.getExternalStorageDirectory().getAbsolutePath() + "/WhatsApp/WhatsApp Video")
    };
    private String[] warningPaths = {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath(),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).getAbsolutePath(),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).getAbsolutePath(),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PODCASTS).getAbsolutePath(),
            Environment.getExternalStorageDirectory().getAbsolutePath() + "/WhatsApp/"
    };

    public InternalFileEnumerator(Boolean testNoDelete) {
        TESTNODELETE = testNoDelete; // for testing use TRUE => not delete any data! And use different ONE destination folder (for easy cleaning)
    } // InternalFileEnumerator

    public boolean readyToMove() {
        if (fileResult != null && fileResult.size() > 0 && SDPATH.length()>0) return true;

        return false;
    } // readyToMove

    private String getUniqueFileSAF(DocumentFile path, String filename) {
        boolean unique = path.findFile(filename)==null;
        if (unique) return filename; // mostly unique, so quick return

        String fname = filename;
        int counter = 1;
        int splitindex = fname.lastIndexOf(".");
        if (splitindex<1) splitindex = fname.length()-1;
        String suffix = fname.substring(splitindex);
        while (!unique) {
            fname = fname.substring(0,splitindex) + "_" + counter++ + suffix;
            unique = path.findFile(fname)==null;
        }
        return fname;
    } // getUniqueFileSAF

    /**
     * Get external sd card path using reflection - manage by is_removable variable if is external storage removable = SD card
     */
    public static String getExternalStoragePath(Context mContext, boolean is_removable) {

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

    // move file to Uri destination by Storage Access Framework (need to be granted access before)
    private Boolean moveFileSAF(Context context, Uri uri, File src) {
        Boolean ret = false;
        try{
            ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(uri, "wa");
            FileOutputStream fos = new FileOutputStream(pfd.getFileDescriptor());
            FileInputStream source = new FileInputStream(src.getAbsoluteFile());

            byte[] buffer = new byte[4096];
            int read = 0;
            while ((read = source.read(buffer)) != -1) {
                if (buffer.length == read) fos.write(buffer);
                else {
                    byte[] buffer2 = new byte[read];
                    buffer2 = Arrays.copyOf(buffer, read);
                    fos.write(buffer2);
                }
            }
            fos.flush();
            fos.close(); pfd.close();
            source.close();
            if (TESTNODELETE) {
                sRetTrace = sRetTrace + "\nWould delete original, but test only.";
            } else {
                // release with deleting original
                src.delete();
            }
            ret = true;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            sRetTrace = sRetTrace + "\nError: Copy file error - " + e.getMessage();
        } catch (Exception e) {
            e.printStackTrace();
            sRetTrace = sRetTrace + "\nError: Copy file error - " + e.getMessage();
        }
        return ret;
    } // moveFileSAF

    String getMimeType(File f) {
        int dot = f.getName().lastIndexOf(".");
        if( dot != -1) {
            MimeTypeMap mime = MimeTypeMap.getSingleton();
            return mime.getMimeTypeFromExtension(f.getName().substring(dot+1));
        } else {
            return null;
        }
    } // getMimeType

    // move file by storage access framework (need to be granted before)
    public String moveFilesBySAF(Context context, Uri uriSD, Boolean bTestmode) {
        bLastrunSuccess = false;
        TESTNODELETE = bTestmode;
        if (SDPATH == null || SDPATH.length()==0) return "\nError: SD-CARD PATH";
        if (fileResult == null || fileResult.isEmpty()) return "\nInfo: Nothing to move!";

        sRetTrace = "";

        try {
            for (FileQueue fg: fileResult) {
                String id = DocumentsContract.getTreeDocumentId(uriSD);
                String destFolder = fg.sDestPath;
                if (TESTNODELETE) destFolder = copyTestFolder;
                String subPath = id + "/" + destFolder;

                File dir = new File(SDPATH + "/" + destFolder);
                sRetTrace = sRetTrace + "\n> Moving to " + dir;
                DocumentFile newDir = null;
                if (!dir.exists()) {
                    sRetTrace = sRetTrace + "\nDir not exist. Creating it.";
                    DocumentFile pickedDir = DocumentFile.fromTreeUri(context, uriSD);
                    newDir = pickedDir.createDirectory(destFolder);
                } else {
                    //newDir = DocumentFile.fromFile(dir);
                    DocumentFile pickedDir = DocumentFile.fromTreeUri(context, uriSD);
                    newDir = pickedDir.findFile(destFolder);
                }
                if (newDir == null) {
                    return sRetTrace + "\nError: Open directory " + dir.getAbsolutePath();
                }

                for (File f : fg.fileList) {
                    String uqFile = getUniqueFileSAF(newDir, f.getName());
                    sRetTrace = sRetTrace + "\nMove file " + f.getName();
                    DocumentFile newFile = newDir.createFile("image/jpg", uqFile);
                    if (newFile == null) return sRetTrace = sRetTrace + "\nError: Create file " + uqFile;
                    if (!moveFileSAF(context, newFile.getUri(), f)) {
                        return sRetTrace + "\nError: Move file " + f.getName();
                    } else sRetTrace = sRetTrace + "\n - File moved successful! (" + uqFile + ")";
                }
                bLastrunSuccess = true;
            }
        } catch (Exception e) {
            sRetTrace = sRetTrace + "\nError: Cannot move file - " + e.getMessage();
        }

        return sRetTrace;
    } // moveLegacyFiles

    private String s_scanDirectoryFiles(File path, String dest, int days) {
        long length = 0;
        long lengthToMove = 0;
        int countToClean = 0;
        int count = 0;

        String ret = "";
        if (path == null) return "";
        if (path.isFile()) return sizeFormat(path.length());
        if (path.listFiles() == null) return "";


        FileQueue fq = new FileQueue();
        fq.sDestPath = dest;

        for (File file : path.listFiles()) {
            if (file.isFile() && !skipFile(file)) {
                long len = file.length();
                length += len;
                count++;
                boolean older = isFileOlderThenDays(file, days);
                if (older) {
                    lengthToMove += len;
                    countToClean++;
                    fq.fileList.add(file);
                    //sRetTrace = sRetTrace + file.getName() + ": " + sizeFormat(len) + "\n";
                }
                //sRetTrace = sRetTrace + file.getName() + ": " + sizeFormat(len) + " older: " + older + "\n";
            }
            // NEJSME REKURZIVNI, TAKZE KDYZ DIR, TAK NEZANORUJEME!!!
        }

        if (count == 0) return "empty";

        if (dest != null && !dest.isEmpty()) {
            fileResult.add(fq);
        }

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
     **/

    // https://stackoverflow.com/questions/30281890/list-the-files-in-download-directory-of-the-android-phone/35862041
    public String scanMemoryForOld(Context context, int days) {
        String sReturn = "";
        LENGTH_TO_CLEAN = 0;
        COUNT_TO_CLEAN = 0;

        sRetTrace = "";
        fileResult.clear();
        bLastrunSuccess = false;

        if (!isMediaAvailable()) {
            return "ERROR: ACCESS MEMORY ERROR!";
        }

        String sdPath = getExternalStoragePath(context, true);
        if (/*!externalMemoryAvailableForWrite() || */sdPath == null) {
            return "ERROR: SD-CARD NOT FOUND OR READ ONLY!";
        } else {
            SDPATH = sdPath;
            // TEST WITHOUT SD CARD:
            // SDPATH = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).getAbsolutePath();
            // sReturn = sReturn + "\nTarget storage: " + SDPATH + "\n";
        }

        if (days>0)
            sReturn = sReturn + "Keeping files used in " + days + " days...\n\n";
        else
            sReturn = sReturn + "Warning! Not recommended to used with keeping zero days set!\n\n";

        sReturn = sReturn + "|| Found to clean: ";
        String tempReturn = "";
        for (SourceDestPath sd: cleaningPaths) {
            File dirToClean = new File(sd.sourcePath);
            if (dirToClean == null) continue;
            if (!dirToClean.exists()) {
                tempReturn = tempReturn + "\n- " + getNiceName(sd.sourcePath) + " not found";
            }
            else {
                String dest = sd.destFolder;
                tempReturn = tempReturn + "\n- " + getNiceName(sd.sourcePath) + " " + s_scanDirectoryFiles(dirToClean, dest, days);
            }
        }

        sReturn = sReturn + sizeFormat(LENGTH_TO_CLEAN) + " / " + COUNT_TO_CLEAN + " items total \n";
        sReturn = sReturn + tempReturn;

        // zazalohujeme, protoze to bude ovlivneno analyzou cest, co se nebudou mazat
        long LENGTH_TO_CLEAN_orig = LENGTH_TO_CLEAN;
        long COUNT_TO_CLEAN_orig = COUNT_TO_CLEAN;

        sReturn = sReturn + "\n\n|| Other analysis - ONLY FOR INFO!\n";
        for (String path: warningPaths) {
            File dirToClean = new File(path);
            if (dirToClean == null) continue;
            if (!dirToClean.exists()) sReturn = sReturn + "\n- " + getNiceName(path) + " not found";
            else sReturn = sReturn + "\n- " + getNiceName(path) + " " + s_scanDirectoryFiles(dirToClean, "", days);
        }

        // obnovime, chceme dale pracovat jen s tim, co budeme fakt mazat
        LENGTH_TO_CLEAN = LENGTH_TO_CLEAN_orig;
        COUNT_TO_CLEAN = COUNT_TO_CLEAN_orig;

        // https://stackoverflow.com/questions/3394765/how-to-check-available-space-on-android-device-on-sd-card
        long freeCard = getSDCardFreeSpace(sdPath);
        sReturn = sReturn + "\n\nSD-CARD found! Path = " + sdPath + "\n| free space " + sizeFormat(freeCard) + "\n";

        bLastrunSuccess = true;

        return sReturn + sRetTrace;
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
