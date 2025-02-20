/**
 *  Copyright 2015 Rover12421 <rover12421@163.com>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

/**
 *  Copyright 2015 Rover12421 <rover12421@163.com>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.rover12421.shaka.apktool.lib;

import brut.androlib.AndrolibException;
import brut.androlib.ApkOptions;
import brut.androlib.res.AndrolibResources;
import brut.androlib.res.data.ResTable;
import brut.androlib.res.decoder.AXmlResourceParser;
import brut.androlib.res.decoder.ResFileDecoder;
import brut.androlib.res.decoder.ResRawStreamDecoder;
import brut.androlib.res.decoder.ResStreamDecoderContainer;
import brut.androlib.res.util.ExtFile;
import brut.util.Duo;
import com.rover12421.shaka.lib.*;
import org.apache.commons.io.IOUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Created by rover12421 on 8/9/14.
 * brut.androlib.res.AndrolibResources
 */
@Aspect
public class AndrolibResourcesAj {
    /**
     * Androidlib.UNK_DIRNAME
     */
    private final static String SHAKA_PNG       = "/png/Shaka.png";
    private final static String SHAKA_9_PNG     = "/png/Shaka.9.png";
    private final static String SHAKA_XML       = "/xml/Shaka.xml";

    public final static Set<String> notDefinedRes = new HashSet<>();

    /**
     * 处理aapt抛出`declared here is not defined`错误
     * @param errInfo
     * @param rootDir
     */
    private boolean fuckNotDefinedRes(String errInfo, String rootDir) {
        if (ShakaBuildOption.getInstance().isFuckNotDefinedRes()) {
            //Public symbol drawable/? declared here is not defined.
            Pattern patternRes = Pattern.compile("Public symbol (.+?) declared here is not defined");
            Matcher matcherRes = patternRes.matcher(errInfo);
            while (matcherRes.find()) {
                String res = matcherRes.group(1);
                String fileName = "res/" + res + ".xml";
                LogHelper.warning("Add temp res : " + fileName);
                notDefinedRes.add(fileName);
            }

            if (notDefinedRes.size() > 0) {
                for (String resName : notDefinedRes) {
                    Path des = Paths.get(rootDir + File.separator + resName);
                    // 保证目录存在
                    des.toFile().getParentFile().mkdirs();
                    InputStream is = this.getClass().getResourceAsStream(SHAKA_XML);
                    try {
                        Files.copy(is, des, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    IOUtils.closeQuietly(is);
                }

                return true;
            }
        }
        return false;
    }

    /**
     * 清理多添加的资源
     */
    private void fuckNotDefinedRes_clearAddRes(File apkFile) throws IOException, ShakaException {
        if (notDefinedRes.size() <= 0) {
            return;
        }

        File tempFile = File.createTempFile(apkFile.getName(), null);
        tempFile.delete();
        tempFile.deleteOnExit();
        boolean renameOk = apkFile.renameTo(tempFile);
        if (!renameOk)
        {
            throw new ShakaException("could not rename the file " + apkFile.getAbsolutePath() + " to " + tempFile.getAbsolutePath());
        }

        try (
                ZipInputStream zin = new ZipInputStream(new FileInputStream(tempFile));
                ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(apkFile))
        ) {
            ZipEntry entry = zin.getNextEntry();
            while (entry != null) {
                String name = entry.getName();
                boolean toBeDeleted = false;
                for (String f : notDefinedRes) {
                    if (f.equals(name)) {
                        toBeDeleted = true;
                        LogHelper.warning("Delete temp res : " + f);
                        break;
                    }
                }
                if (!toBeDeleted) {
                    // Add ZIP entry to output stream.
                    zout.putNextEntry(new ZipEntry(name));
                    // Transfer bytes from the ZIP file to the output file
                    IOUtils.copy(zin, zout);
                }
                entry = zin.getNextEntry();
            }
        }

        tempFile.delete();
        notDefinedRes.clear();
    }

    private boolean horizontalScrollView_check(String errInfo) throws Exception {
        if (errInfo.indexOf("'@android:style/Widget.HorizontalScrollView'") > 0) {
            Pattern xmlPathPattern = Pattern.compile("(.+?):\\d+:.+?(Error retrieving parent for item).+?'@android:style/Widget.HorizontalScrollView'");
            Matcher xmlPathMatcher = xmlPathPattern.matcher(errInfo);
            if (xmlPathMatcher.find()) {
                String xmlPathStr = xmlPathMatcher.group(1);
                File xmlPathFile = new File(xmlPathStr);
                if (xmlPathFile.exists()) {
                    LogHelper.warning("Find HorizontalScrollView exception xml : " + xmlPathStr);
                    DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
                    DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
                    Document document = documentBuilder.parse(xmlPathStr);
                    NodeList list = document.getChildNodes().item(0).getChildNodes();
                    for (int i=0; i<list.getLength(); i++) {
                        Node node = list.item(i);
                        if (node.getAttributes() != null) {
                            Node attr = node.getAttributes().getNamedItem("parent");
                            if (attr != null && attr.getNodeValue().equals("@android:style/Widget.HorizontalScrollView")) {
                                /**
                                 * 首先把HorizontalScrollView替换成aapt能识别的ScrollView
                                 */
                                attr.setNodeValue("@android:style/Widget.ScrollView");
                                /**
                                 * 再判断时候有 android:scrollbars 和 android:fadingEdge 属性
                                 * 有的话不修改,没有就添加
                                 * 这样是为了保持样式不变化
                                 */
                                Element scrollbars = document.createElement("item");
                                scrollbars.setAttribute("name", "android:scrollbars");
                                scrollbars.setNodeValue("horizontal");

                                Element fadingEdge = document.createElement("item");
                                fadingEdge.setAttribute("name", "android:fadingEdge");
                                fadingEdge.setNodeValue("horizontal");
                                Element element = (Element) node;
                                NodeList items = element.getElementsByTagName("item");
                                for (int j=0; j<items.getLength(); j++) {
                                    Element item = (Element) items.item(j);
                                    if (item.getAttribute("name").equals("android:scrollbars")) {
                                        scrollbars = null;
                                    }
                                    if (item.getAttribute("name").equals("android:fadingEdge")) {
                                        fadingEdge = null;
                                    }
                                }

                                if (scrollbars != null) {
                                    node.appendChild(scrollbars);
                                }

                                if (fadingEdge != null) {
                                    node.appendChild(fadingEdge);
                                }
                            }
                        }
                    }

                    /**
                     * 保存修改过的xml
                     */
                    TransformerFactory transformerFactory = TransformerFactory.newInstance();
                    Transformer transformer = transformerFactory.newTransformer();
                    transformer.transform(new DOMSource(document), new StreamResult(xmlPathStr));

                    return true;
                }
            }
        }

        return false;
    }

    private boolean checkPng(String errInfo, String rootDir) {
        //Androlib.class, "UNK_DIRNAME"
        String UNK_DIRNAME = "unknown";

        Pattern patternPng = Pattern.compile("ERROR: Failure processing PNG image (.+)");
        Pattern pattern9Png = Pattern.compile("ERROR: 9-patch image (.+) malformed\\.");


        Matcher matcherPng = patternPng.matcher(errInfo);
        Matcher matcher9Png = pattern9Png.matcher(errInfo);

        Map<String, String> replacePng = new HashMap<>();

        while (matcherPng.find()) {
            String png = matcherPng.group(1);
            String desPath = rootDir + File.separatorChar + UNK_DIRNAME + png.substring(rootDir.length());
            replacePng.put(png, desPath);
        }

        while (matcher9Png.find()) {
            String png = matcher9Png.group(1);
            String desPath = rootDir + File.separatorChar + UNK_DIRNAME + png.substring(rootDir.length());
            replacePng.put(png, desPath);
        }

        if (replacePng.size() > 0 ) {
            for (String srcPng : replacePng.keySet()) {
                if (!new File(srcPng).exists()) {
                    /**
                     * 文件不存在.跳过.
                     * 发现错误流有被篡写的现象
                     */
                    continue;
                }

                String desPng = replacePng.get(srcPng);
                //创建目录
                new File(desPng).getParentFile().mkdirs();

                try {
                    //备份原始文件
                    Path srcPath = Paths.get(srcPng);
                    Path desPath = Paths.get(desPng);
                    LogHelper.warning("Found exception png file : " + srcPng);
                    Files.copy(srcPath, desPath, StandardCopyOption.REPLACE_EXISTING);

                    //用ok的png替换异常png
                    InputStream pngIs;
                    if (srcPng.endsWith(".9.png")) {
                        pngIs = this.getClass().getResourceAsStream(SHAKA_9_PNG);
                    } else {
                        pngIs = this.getClass().getResourceAsStream(SHAKA_PNG);
                    }
                    Files.copy(pngIs, srcPath, StandardCopyOption.REPLACE_EXISTING);
                    IOUtils.closeQuietly(pngIs);
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }

            return true;
        }

        return false;
    }

    public static Collection<String> doNotCompress = null;

    public void doNotCompress_in_aapt_fix(AndrolibResources androlibResources) {
        /**
         * https://github.com/iBotPeaches/Apktool/issues/1071
         * 可以看出OSX也有这个问题,不再判断系统,所有平台使用相同操作
         */
//        if (!EnvironmentDetection.isWindows()) {
//            return;
//        }

        if (doNotCompress != null) {
            return;
        }

        ApkOptions apkOptions = androlibResources.apkOptions;
        doNotCompress = apkOptions.doNotCompress;
        apkOptions.doNotCompress = null;
    }

    /**
     * 异常png图片处理
     * brut.androlib.res.AndrolibResources
     * public void aaptPackage(File apkFile, File manifest, File resDir, File rawDir, File assetDir, File[] include)
     */
    @Around("execution(* brut.androlib.res.AndrolibResources.aaptPackage(..))" +
            "&& args(apkFile, manifest, resDir, rawDir, assetDir, include)")
    public void aaptPackage_around(ProceedingJoinPoint joinPoint,
                                   File apkFile, File manifest, File resDir, File rawDir, File assetDir, File[] include) throws Throwable {

        AndrolibResources thiz = (AndrolibResources) joinPoint.getThis();
        doNotCompress_in_aapt_fix(thiz);

        /**
         * 最大尝试10次,防止无限循环
         */
        int max = 10;
        PrintStream olderr = System.err;
        String lastErrInfo = null;
        while (max-- > 0) {

            try (
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    PrintStream ps = new PrintStream(baos)
            ) {
                System.setErr(ps);
                try {
                    joinPoint.proceed(joinPoint.getArgs());
                    System.setErr(olderr);
                    /**
                     * 只有正常执行才需要清理
                     */
                    fuckNotDefinedRes_clearAddRes(apkFile);
                    break;
                } catch (Throwable e) {
                    System.setErr(olderr);

                    String errStr = new String(baos.toByteArray());

                    /**
                     * 两个错误相同,说明该错误无法处理,直接抛出异常
                     */
                    if (errStr.equals(lastErrInfo)) {
                        throw new ShakaException(errStr, e);
                    }
                    lastErrInfo = errStr;

                    String rootDir = manifest.getParentFile().getAbsolutePath();

                    boolean bContinue = false;
                    if (checkPng(errStr, rootDir)) {
                        bContinue = true;
                    }

                    if (horizontalScrollView_check(errStr)) {
                        bContinue = true;
                    }

                    if (!bContinue) {
                        //需要最后才处理,避免和其他的处理起冲突
                        bContinue = fuckNotDefinedRes(errStr, rootDir);
                    }

                    if (!bContinue) {
                        throw new ShakaException(errStr, e);
                    }
                } finally {
                    System.setErr(olderr);
                }
            }
        }

    }

    @AfterReturning(pointcut = "execution(* brut.androlib.res.AndrolibResources.getResFileDecoder(..))", returning = "duo")
    public void getResFileDecoder_after(Duo<ResFileDecoder, AXmlResourceParser> duo) {
        if (!ShakaDecodeOption.getInstance().isNo9png()) {
            return;
        }
        ResFileDecoder fileDecoder = duo.m1;
        try {
            ResStreamDecoderContainer mDecoders = fileDecoder.getDecoders();
            mDecoders.setDecoder("9patch", new ResRawStreamDecoder());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @AfterReturning(pointcut = "execution(* brut.androlib.res.AndrolibResources.getFrameworkApk(..))" +
            "&& args(id, frameTag)", returning = "apk")
    public void getFrameworkApk(int id, String frameTag, File apk) throws AndrolibException {
        if (!ShakaDecodeOption.getInstance().isUsingDefaultFramework()) {
            return;
        }

        if (id == 1 && apk.getAbsolutePath().endsWith("1.apk")) {
            try (InputStream in = AndrolibResources.class.getResourceAsStream("/brut/androlib/android-framework.jar");
                 OutputStream out = new FileOutputStream(apk)) {
                IOUtils.copy(in, out);
            } catch (IOException ex) {
                throw new AndrolibException(ex);
            }
        }
    }

    @Around("execution(* brut.androlib.res.AndrolibResources.getAaptBinaryFile())")
    public File getAaptBinaryFile() throws ShakaException {
        try {
            return new File(ShakaApktoolFiles.getShakaAaptBinPath());
        } catch (Throwable e) {
            LogHelper.info("Can't set aapt binary as executable");
            throw new ShakaException("Can't set aapt binary as executable", e);
        }
    }


    private static ExtFile apkFile;
    private static File outDir;

    @Before("execution(* brut.androlib.res.AndrolibResources.decode(..))" +
            "&& args(resTable, apkFile, outDir)")
    public void decode(ResTable resTable, ExtFile apkFile, File outDir) {
        AndrolibResourcesAj.apkFile = apkFile;
        AndrolibResourcesAj.outDir = outDir;
    }

    public static ExtFile getApkFile() {
        return apkFile;
    }

    public static File getOutDir() {
        return outDir;
    }

    @Before("execution(* brut.androlib.res.AndrolibResources.generatePublicXml(..))")
    public void generatePublicXml() throws AndrolibException {
        ResFileDecoderAj.ReDecodeFiles();
    }

    @Before("execution(* brut.androlib.res.AndrolibResources.setVersionInfo(..))" +
            "&& args(map)")
    public void setVersionInfo(Map<String, String> map) {
        if (map != null) {
            String mVersionName = map.get("versionName");
            if (mVersionName != null && mVersionName.contains(" ")) {
                //versionName包含空白字符会有问题
                String mVersionNameNew = mVersionName.replaceAll(" ", "_");
                LogHelper.info("Modify versionName from : " + mVersionName + " to : " + mVersionNameNew);
                map.put("versionName", mVersionNameNew);
            }
        }
    }
}
