/**
 * Copyright 2008 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tonicsystems.jarjar.util;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

class IoUtil {
    private IoUtil() {}

    public static void pipe(InputStream is, OutputStream out, byte[] buf) throws IOException {
        for (;;) {
            int amt = is.read(buf);
            if (amt < 0)
                break;
            out.write(buf, 0, amt);
        }
    }

    public static void copy(File from, File to, byte[] buf) throws IOException {
        InputStream in = new FileInputStream(from);
        try {
            OutputStream out = new FileOutputStream(to);
            try {
                pipe(in, out, buf);
            } finally {
                out.close();
            }
        } finally {
            in.close();
        }
    }

    /**
     * Create a copy of an zip file without its empty directories.
     * @param inputFile
     * @param outputFile
     * @throws IOException
     */
    public static void copyZipWithoutEmptyDirectories(final File inputFile, final File outputFile) throws IOException
    {
        final byte[] buf = new byte[0x2000];

        final ZipFile inputZip = new ZipFile(inputFile);
        final ZipOutputStream outputStream = new ZipOutputStream(new FileOutputStream(outputFile));
        try
        {
            // read a the entries of the input zip file and sort them
            final Enumeration<? extends ZipEntry> e = inputZip.entries();
            final ArrayList<ZipEntry> sortedList = new ArrayList<ZipEntry>();
            while (e.hasMoreElements()) {
                final ZipEntry entry = e.nextElement();
                // META-INF/ doesn't need a directory entry
                if (!"META-INF/".equals(entry.getName())) {
                    sortedList.add(entry);
                }
            }

            Collections.sort(sortedList, new Comparator<ZipEntry>()
            {
                public int compare(ZipEntry o1, ZipEntry o2)
                {
                    String n1 = o1.getName(), n2 = o2.getName();
                    if (metaOverride(n1, n2)) {
                        return -1;
                    }
                    if (metaOverride(n2, n1)) {
                        return 1;
                    }
                    return n1.compareTo(n2);
                }

                // make sure that META-INF/MANIFEST.MF is always the very first entry
                private boolean metaOverride(String n1, String n2) {
                    return (n1.startsWith("META-INF/") && !n2.startsWith("META-INF/"))
                        || (n1.equals("META-INF/MANIFEST.MF") && !n2.equals(n1));
                }
            });

            // treat them again and write them in output, wenn they not are empty directories
            for (int i = sortedList.size()-1; i>=0; i--)
            {
                final ZipEntry inputEntry = sortedList.get(i);
                final String name = inputEntry.getName();
                final boolean isEmptyDirectory;
                if (inputEntry.isDirectory())
                {
                    if (i == sortedList.size()-1)
                    {
                        // no item afterwards; it was an empty directory
                        isEmptyDirectory = true;
                    }
                    else
                    {
                        final String nextName = sortedList.get(i+1).getName();
                        isEmptyDirectory  = !nextName.startsWith(name);
                    }
                }
                else
                {
                    isEmptyDirectory = false;
                }

                if (isEmptyDirectory)
                {
                    sortedList.remove(i);
                }
            }

            // finally write entries in normal order
            for (int i = 0; i < sortedList.size(); i++)
            {
                final ZipEntry inputEntry = sortedList.get(i);
                final ZipEntry outputEntry = new ZipEntry(inputEntry);
                outputStream.putNextEntry(outputEntry);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                final InputStream is = inputZip.getInputStream(inputEntry);
                IoUtil.pipe(is, baos, buf);
                is.close();
                outputStream.write(baos.toByteArray());
            }
        } finally {
            outputStream.close();
            inputZip.close();
        }

    }

}
