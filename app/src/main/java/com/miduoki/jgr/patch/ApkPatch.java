package com.miduoki.jgr.patch;

import static com.miduoki.jgr.Constants.CONFIG_ASSET_PATH;
import static com.miduoki.jgr.Constants.DEX_ASSET_PATH;
import static com.miduoki.jgr.Constants.ORIGINAL_APK_ASSET_PATH;
import static com.miduoki.jgr.Constants.PROXY_APP_COMPONENT_FACTORY;


import com.android.tools.build.apkzlib.sign.SigningExtension;
import com.android.tools.build.apkzlib.sign.SigningOptions;
import com.android.tools.build.apkzlib.zip.AlignmentRules;
import com.android.tools.build.apkzlib.zip.NestedZip;
import com.android.tools.build.apkzlib.zip.StoredEntry;
import com.android.tools.build.apkzlib.zip.ZFile;
import com.android.tools.build.apkzlib.zip.ZFileOptions;
import com.google.gson.Gson;
import com.miduoki.jgr.JLOG;
import com.miduoki.jgr.utils.ApkSignatureHelper;
import com.miduoki.jgr.utils.ManifestParser;
import com.wind.meditor.core.ManifestEditor;
import com.wind.meditor.property.AttributeItem;
import com.wind.meditor.property.ModificationProperty;
import com.wind.meditor.utils.NodeValue;

import org.apache.commons.io.FilenameUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;


public class ApkPatch {

    private boolean forceOverwrite = false;
    private boolean overrideVersionCode = false;
    private boolean debuggableFlag = false;
    private int sigbypassLevel = 0;
    private boolean useManager = false;

    private List<String> keystoreArgs = Arrays.asList(null, "123456", "key0", "123456");
    private static final String ANDROID_MANIFEST_XML = "AndroidManifest.xml";
    private List<String> modules = new ArrayList<>();

    private static final HashSet<String> APK_LIB_PATH_ARRAY = new HashSet<>(Arrays.asList(
            "arm",
            "arm64",
            "x86",
            "x86_64"
    ));

    private static final HashSet<String> ARCHES = new HashSet<>(Arrays.asList(
            "armeabi-v7a",
            "arm64-v8a",
            "x86",
            "x86_64"
    ));


    static class PatchError extends Error {
        public PatchError(String message, Throwable cause) {
            super(message, cause);
        }

        PatchError(String message) {
            super(message);
        }
    }

    public void doCommandLine(List<String> apkPaths,String outputPath) throws PatchError, IOException {
        for (String apk : apkPaths) {
            File srcApkFile = new File(apk).getAbsoluteFile();

            String apkFileName = srcApkFile.getName();

            File outputDir = new File(outputPath);
            outputDir.mkdirs();

            File outputFile = new File(outputDir, String.format(
                    Locale.getDefault(), "%s-%d-lspatched.apk",
                    FilenameUtils.getBaseName(apkFileName),
                    JGRConfig.instance.VERSION_CODE)
            ).getAbsoluteFile();

            if (outputFile.exists() && !forceOverwrite)
                throw new PatchError(outputPath + " exists. Use --force to overwrite");

            //内部存储 看都看不到
            JLOG.i("Processing " + srcApkFile + " -> " + outputFile);

            //patch(srcApkFile, outputFile);
        }
    }

    private static final ZFileOptions Z_FILE_OPTIONS = new ZFileOptions().setAlignmentRule(AlignmentRules.compose(
            AlignmentRules.constantForSuffix(".so", 4096),
            AlignmentRules.constantForSuffix(ORIGINAL_APK_ASSET_PATH, 4096)
    ));

    public void patch(File srcApkFile, File outputFile) throws PatchError, IOException {
        if (!srcApkFile.exists())
            throw new PatchError("The source apk file does not exit. Please provide a correct path.");

        outputFile.delete();

        JLOG.d("apk path: " + srcApkFile);

        JLOG.i("Parsing original apk...");

        ZFile dstZFile = ZFile.openReadWrite(outputFile, Z_FILE_OPTIONS);
        NestedZip srcZFile = dstZFile.addNestedZip((ignore) -> ORIGINAL_APK_ASSET_PATH, srcApkFile, false);

        // sign apk
        try {
            var keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            if (keystoreArgs.get(0) == null) {
                JLOG.i("Register apk signer with default keystore...");
                try (var is = getClass().getClassLoader().getResourceAsStream("assets/keystore")) {
                    keyStore.load(is, keystoreArgs.get(1).toCharArray());
                }
            } else {
                JLOG.i("Register apk signer with custom keystore...");
                try (var is = new FileInputStream(keystoreArgs.get(0))) {
                    keyStore.load(is, keystoreArgs.get(1).toCharArray());
                }
            }
            var entry = (KeyStore.PrivateKeyEntry) keyStore.getEntry(keystoreArgs.get(2), new KeyStore.PasswordProtection(keystoreArgs.get(3).toCharArray()));
            new SigningExtension(SigningOptions.builder()
                    .setMinSdkVersion(28)
                    .setV2SigningEnabled(true)
                    .setCertificates((X509Certificate[]) entry.getCertificateChain())
                    .setKey(entry.getPrivateKey())
                    .build()).register(dstZFile);
        } catch (Exception e) {
            throw new PatchError("Failed to register signer", e);
        }

        final String originalSignature = ApkSignatureHelper.getApkSignInfo(srcApkFile.getAbsolutePath());
        if (originalSignature == null || originalSignature.isEmpty()) {
            throw new PatchError("get original signature failed");
        }
        JLOG.d("Original signature\n" + originalSignature);

        // copy out manifest file from zlib
        var manifestEntry = srcZFile.get(ANDROID_MANIFEST_XML);
        if (manifestEntry == null)
            throw new PatchError("Provided file is not a valid apk");

        // parse the app appComponentFactory full name from the manifest file
        final String appComponentFactory;
        try (var is = manifestEntry.open()) {
            var pair = ManifestParser.parseManifestFile(is);
            if (pair == null)
                throw new PatchError("Failed to parse AndroidManifest.xml");
            appComponentFactory = pair.appComponentFactory;
            JLOG.d("original appComponentFactory class: " + appComponentFactory);
        }

        JLOG.i("Patching apk...");
        // modify manifest
        final var config = new PatchConfig(useManager, debuggableFlag, overrideVersionCode, sigbypassLevel, originalSignature, appComponentFactory);
        final var configBytes = new Gson().toJson(config).getBytes(StandardCharsets.UTF_8);
        final var metadata = Base64.getEncoder().encodeToString(configBytes);
        try (var is = new ByteArrayInputStream(modifyManifestFile(manifestEntry.open(), metadata))) {
            dstZFile.add(ANDROID_MANIFEST_XML, is);
        } catch (Throwable e) {
            throw new PatchError("Error when modifying manifest", e);
        }

        JLOG.d("Adding native lib..");

        // copy so and dex files into the unzipped apk
        // do not put liblspd.so into apk!lib because x86 native bridge causes crash
        for (String arch : APK_LIB_PATH_ARRAY) {
            String entryName = "assets/lspatch/so/" + arch + "/liblspatch.so";
            try (var is = getClass().getClassLoader().getResourceAsStream("assets/so/" + (arch.equals("arm") ? "armeabi-v7a" : (arch.equals("arm64") ? "arm64-v8a" : arch)) + "/liblspatch.so")) {
                dstZFile.add(entryName, is, false); // no compress for so
            } catch (Throwable e) {
                // More exception info
                throw new PatchError("Error when adding native lib", e);
            }
            JLOG.d("added " + entryName);
        }

        JLOG.d("Adding dex..");

        try (var is = getClass().getClassLoader().getResourceAsStream("assets/dex/loader.dex")) {
            dstZFile.add("classes.dex", is);
        } catch (Throwable e) {
            throw new PatchError("Error when adding dex", e);
        }

        try (var is = getClass().getClassLoader().getResourceAsStream("assets/dex/lsp.dex")) {
            dstZFile.add(DEX_ASSET_PATH, is);
        } catch (Throwable e) {
            throw new PatchError("Error when adding assets", e);
        }

        // save lspatch config to asset..
        try (var is = new ByteArrayInputStream(configBytes)) {
            dstZFile.add(CONFIG_ASSET_PATH, is);
        } catch (Throwable e) {
            throw new PatchError("Error when saving config");
        }

        Set<String> apkArches = new HashSet<>();

        JLOG.d("Search target apk library arch...");

        for (StoredEntry storedEntry : srcZFile.entries()) {
            var name = storedEntry.getCentralDirectoryHeader().getName();
            if (name.startsWith("lib/") && name.length() >= 5) {
                var arch = name.substring(4, name.indexOf('/', 5));
                apkArches.add(arch);
            }
        }
        if (apkArches.isEmpty()) apkArches.addAll(ARCHES);
        apkArches.removeIf((arch) -> {
            if (!ARCHES.contains(arch) && !arch.equals("armeabi")) {
                JLOG.e("Warning: unsupported arch " + arch + ". Skipping...");
                return true;
            }
            return false;
        });

        if (!useManager) {
            embedModules(dstZFile);
        }

        // create zip link
        JLOG.d("Creating nested apk link...");

        for (StoredEntry entry : srcZFile.entries()) {
            String name = entry.getCentralDirectoryHeader().getName();
            if (name.startsWith("classes") && name.endsWith(".dex")) continue;
            if (dstZFile.get(name) != null) continue;
            if (name.equals("AndroidManifest.xml")) continue;
            if (name.startsWith("META-INF") && (name.endsWith(".SF") || name.endsWith(".MF") || name.endsWith(".RSA"))) continue;
            srcZFile.addFileLink(name, name);
        }

        dstZFile.realign();

        JLOG.i("Writing apk...");

    }

    private byte[] modifyManifestFile(InputStream is, String metadata) throws IOException {
        ModificationProperty property = new ModificationProperty();

        if (overrideVersionCode)
            property.addManifestAttribute(new AttributeItem(NodeValue.Manifest.VERSION_CODE, 1));
        property.addApplicationAttribute(new AttributeItem(NodeValue.Application.DEBUGGABLE, debuggableFlag));
        property.addApplicationAttribute(new AttributeItem("appComponentFactory", PROXY_APP_COMPONENT_FACTORY));
        property.addMetaData(new ModificationProperty.MetaData("lspatch", metadata));
        // TODO: replace query_all with queries -> manager
        property.addUsesPermission("android.permission.QUERY_ALL_PACKAGES");

        var os = new ByteArrayOutputStream();
        (new ManifestEditor(is, os, property)).processManifest();
        is.close();
        os.flush();
        os.close();
        return os.toByteArray();
    }

    private void embedModules(ZFile zFile) {
        JLOG.i("Embedding modules...");
        for (var module : modules) {
            File file = new File(module);
            try (var apk = ZFile.openReadOnly(new File(module));
                 var fileIs = new FileInputStream(file);
                 var xmlIs = Objects.requireNonNull(apk.get(ANDROID_MANIFEST_XML)).open()
            ) {
                var manifest = Objects.requireNonNull(ManifestParser.parseManifestFile(xmlIs));
                var packageName = manifest.packageName;
                JLOG.i("  - " + packageName);
                zFile.add("assets/lspatch/modules/" + packageName + ".apk", fileIs);
            } catch (NullPointerException | IOException e) {
                JLOG.e(module + " does not exist or is not a valid apk file.");
            }
        }
    }
}
