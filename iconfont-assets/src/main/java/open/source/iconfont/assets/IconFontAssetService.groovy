package open.source.iconfont.assets

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface IconFontAssetService {
    @GET("/icon-font-assets/index.json")
    Call<IconFontAsset> index(@Query("version") String version)
}