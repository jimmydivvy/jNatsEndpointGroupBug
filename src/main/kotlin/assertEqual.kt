val crossSymbol = "\u274C"
val tickSymbol = "\u2705"


fun assertEqual(description:String, expected:String, actual:String){
    if( expected == actual ){
        println("${tickSymbol} ${description}: ${actual}")
    } else {
        println("${crossSymbol} ${description}: ${actual} (Expected: ${expected})")
    }

}