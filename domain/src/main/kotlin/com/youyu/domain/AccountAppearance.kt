package com.youyu.domain

data class Institution(val id: String, val name: String, val group: String, val aliases: List<String>) {
    val terms get() = listOf(name, id) + aliases
}
data class CardFace(val id: String, val name: String, val group: String)

object AccountAppearance {
    val institutions = listOf(
        Institution("icbc", "中国工商银行", "银行", listOf("工商银行", "工行", "industrial and commercial bank of china")),
        Institution("abc", "中国农业银行", "银行", listOf("农业银行", "农行", "agricultural bank of china")),
        Institution("boc", "中国银行", "银行", listOf("中行", "bank of china")),
        Institution("ccb", "中国建设银行", "银行", listOf("建设银行", "建行", "china construction bank")),
        Institution("bcm", "交通银行", "银行", listOf("交行", "bocom", "comm", "bank of communications")),
        Institution("psbc", "中国邮政储蓄银行", "银行", listOf("邮政储蓄", "邮储", "postal savings bank of china")),
        Institution("cmb", "招商银行", "银行", listOf("招行", "china merchants bank")),
        Institution("citic", "中信银行", "银行", listOf("中信", "citic bank")),
        Institution("cmbc", "中国民生银行", "银行", listOf("民生银行", "民生")),
        Institution("cib", "兴业银行", "银行", listOf("兴业")),
        Institution("spdb", "浦发银行", "银行", listOf("浦发", "上海浦东发展银行")),
        Institution("pab", "平安银行", "银行", listOf("平安", "ping an bank")),
        Institution("ceb", "中国光大银行", "银行", listOf("光大银行", "光大", "cebbank")),
        Institution("hxb", "华夏银行", "银行", listOf("华夏", "hxbank")),
        Institution("cgb", "广发银行", "银行", listOf("广发")),
        Institution("czb", "浙商银行", "银行", listOf("浙商", "czbank")),
        Institution("suzhou", "苏州银行", "银行", listOf("bank of suzhou", "suzhoubank")),
        Institution("hangzhou", "杭州银行", "银行", listOf("bank of hangzhou", "hzbank")),
        Institution("webank", "微众银行", "银行", listOf("微众")),
        Institution("mybank", "网商银行", "银行", listOf("网商")),
        Institution("wechatpay", "微信支付", "支付与钱包", listOf("微信", "零钱通", "零钱", "wechat", "wechat pay")),
        Institution("alipay", "支付宝", "支付与钱包", listOf("余额宝", "花呗", "huabei")),
        Institution("unionpay", "云闪付", "支付与钱包", listOf("银联", "union pay")),
        Institution("jdfinance", "京东金融", "支付与钱包", listOf("京东白条", "京东支付", "jd pay")),
        Institution("wise", "Wise", "海外账户与券商", listOf("transferwise")),
        Institution("nordea", "Nordea", "海外账户与券商", listOf("北欧联合银行")),
        Institution("ibkr", "Interactive Brokers", "海外账户与券商", listOf("盈透", "盈透证券")),
    )
    val faces = listOf(
        CardFace("quiet", "素笺", "素色"),
        CardFace("inkIvory", "鹤白", "水墨"),
        CardFace("inkPair", "相映", "水墨"),
        CardFace("inkGold", "点金", "水墨"),
        CardFace("oceanSilver", "银流", "深海"),
        CardFace("oceanWhale", "鲸蓝", "深海"),
        CardFace("deepBloom", "云鳐", "深海"),
        CardFace("paperRed", "双锦", "剪纸"),
        CardFace("paperBlue", "逐浪", "剪纸"),
        CardFace("paperSand", "望日", "剪纸"),
        CardFace("starryReserve", "月泊", "风景"),
        CardFace("springLedger", "潮汐", "风景"),
        CardFace("goldenHarvest", "沙丘", "风景"),
    )
    private val tokens = institutions.flatMap { bank -> bank.terms.map { it.lowercase() to bank.id } }.sortedByDescending { it.first.length }
    private val separators = Regex("[^a-z]+")
    fun icon(name: String, kind: AccountKind, selected: String?): String {
        if (selected != null) return selected
        val label = name.lowercase()
        val words = " " + label.replace(separators, " ") + " "
        val bank = tokens.firstOrNull { (token, _) ->
            if (token.all { it.code < 128 }) " $token " in words else token in label
        }?.second
        if (bank != null) return "bank:$bank"
        return "symbol:" + when (kind) {
            AccountKind.CASH -> "wallet"
            AccountKind.CREDIT, AccountKind.DEBIT -> "card"
            AccountKind.SAVINGS -> "savings"
            AccountKind.INVESTMENT -> "chart"
            AccountKind.LOAN -> "loan"
            AccountKind.OTHER -> "bank"
        }
    }
}
