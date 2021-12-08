package open.source.iconfont.assets.test;

import com.google.gson.Gson;

import java.io.IOException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import open.source.iconfont.assets.IconFontAssetService;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class Main {
    public static void main(String[] args) {
        OkHttpClient client = new OkHttpClient();
        IconFontAssetService service = new Retrofit.Builder()
                .client(client)
                .baseUrl("https://icon-font-assets.oss-cn-beijing.aliyuncs.com")
                .addConverterFactory(GsonConverterFactory.create(new Gson()))
                .build()
                .create(IconFontAssetService.class);
        try {
            System.out.println(service.index("0.0.1").execute().body().icons);
            client.newCall(new Request.Builder()
                    .url("https://icon-font-assets.oss-cn-beijing.aliyuncs.com/icon-font-assets/iconfont.ttf")
                    .build()).execute();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
