package open.source.iconfont.assets

import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.TaskAction
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

import java.nio.file.Paths
import java.util.concurrent.Callable
import java.util.function.Function

class GenAssetsTask extends DefaultTask {

    private final Gson gson

    GenAssetsTask() {
        gson = new Gson()
    }

    private File getXmlDir() {
        return project.file(Paths.get(
                "build",
                "generated",
                "main",
                "res",
                "drawable"
        ))
    }

    @OutputFiles
    File[] getXmlFiles() {
        def files = getXmlDir().listFiles(new FileFilter() {
            @Override
            boolean accept(File file) {
                return file.isFile() && file.name.endsWith(".xml")
            }
        })
        return files == null ? new File[0] : files
    }

    private String getFontName() {
        def version = project.extensions.iconfont.version
        return "icon_font_" + toName(version)
    }

    @OutputFile
    File getTypefaceFile() {

        return project.file(Paths.get(
                "build",
                "generated",
                "main",
                "res",
                "font",
                getFontName() + ".ttf"
        ))
    }

    private static <T> T fetch(Callable<T> callable, Function<Throwable, Throwable> factory) {
        def result = null
        try {
            result = callable.call()
            if (result == null) {
                throw new NullPointerException()
            }
        } catch (Throwable e) {
            throw factory.apply(e)
        }
        return result
    }

    @TaskAction
    void action() {
        String version = project.extensions.iconfont.version
        String host = project.extensions.iconfont.host
        if (version == null || version.isEmpty()) {
            throw new IllegalArgumentException(
                    "请在build.gradle中设置iconfont字体包的版本，例如：iconfont{ version = \"1.0.0\" }"
            )
        }
        if (host == null || host.isEmpty()) {
            throw new IllegalArgumentException(
                    "请在build.gradle中设置iconfont接口地址，例如：iconfont{ host = \"http://255.255.255\" }"
            )
        }
        def client = new OkHttpClient()
        def service = new Retrofit.Builder()
                .client(client)
                .baseUrl(host)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build()
                .create(IconFontAssetService.class)
        def tuple = fetch(new Callable<Tuple2<IconFontAsset, String>>() {
            @Override
            Tuple2<IconFontAsset, String> call() throws Exception {
                def response = service.index(version)
                        .execute()
                def body = response.body()
                if (response.code() == 200
                        && body != null
                        && body.fonts != null
                        && !body.fonts.isEmpty()
                        && body.icons != null
                        && !body.icons.isEmpty()) {
                    def fontUrl = body.fonts.find { it -> return it.endsWith(".ttf") }
                    if (fontUrl != null) {
                        return new Tuple2<>(body, fontUrl)
                    }
                }
                return null
            }
        }, new Function<Throwable, Throwable>() {
            @Override
            Throwable apply(Throwable throwable) {
                return new IllegalStateException(host + "出问题了", throwable)
            }
        })
        def body = tuple.first
        def fontUrl = tuple.second
        def fontBytes = fetch(new Callable<byte[]>() {
            @Override
            byte[] call() throws Exception {
                def response = client.newCall(new Request.Builder()
                        .url(fontUrl)
                        .build()
                ).execute()
                if (response.code() == 200) {
                    return response.body().bytes()
                }
                return null
            }
        }, new Function<Throwable, Throwable>() {
            @Override
            Throwable apply(Throwable throwable) {
                return new IllegalStateException("拉取字体文件失败", throwable)
            }
        })
        def fontFile = getTypefaceFile()
        if (fontFile.exists()) {
            fontFile.delete()
        } else {
            fontFile.getParentFile().mkdir()
        }
        fontFile.createNewFile()
        fontFile.setBytes(fontBytes)
        def xmlDir = getXmlDir()
        if (xmlDir.exists()) {
            xmlDir.deleteDir()
        }
        xmlDir.mkdir()
        def fontName = getFontName()
        for (def item : body.icons) {
            def name = "ref_" + toName(item.key)
            def file = new File(xmlDir, name + ".xml")
            file.createNewFile()
            def writer = file.newWriter()
            def value = item.value.replace('\\', '')
            writer.writeLine("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<icon-font xmlns:app=\"http://schemas.android.com/apk/res-auto\"")
            writer.writeLine("app:code=\"" + value + "\"")
            writer.writeLine("app:font=\"@font/" + fontName + "\"")
            writer.writeLine("/>")
            writer.close()
        }
    }

    private static String toName(String source) {
        def builder = new StringBuilder(source.length())
        for (int i = 0; i < source.length(); i++) {
            def c = source.charAt(i)
            def b = (byte) c
            if ((b >= 97 && b <= 122) || (b >= 65 && b <= 90)) {
                builder.append(c.toLowerCase())
            } else if (c.isDigit()) {
                builder.append(c)
            } else if (i != 0 && i != source.length() - 1) {
                builder.append('_')
            }
        }
        return builder.toString()
    }

}