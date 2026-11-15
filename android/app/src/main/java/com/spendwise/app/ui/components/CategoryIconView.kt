package com.spendwise.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun CategoryIconView(
    iconName: String,
    colorHex: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp
) {
    val color = runCatching { Color(android.graphics.Color.parseColor(colorHex)) }.getOrDefault(Color(0xFF6366F1))
    val imageVector = getIconVector(iconName)

    Box(
        modifier = modifier
            .size(size)
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = iconName,
            tint = color,
            modifier = Modifier.size(iconSize)
        )
    }
}

fun getIconVector(name: String): ImageVector {
    return when (name.lowercase()) {
        "utensils", "food" -> Icons.Default.Restaurant
        "car", "transportation" -> Icons.Default.DirectionsCar
        "shoppingbag", "shopping" -> Icons.Default.ShoppingBag
        "film", "entertainment" -> Icons.Default.Movie
        "zap", "bills" -> Icons.Default.Bolt
        "heartpulse", "healthcare" -> Icons.Default.Favorite
        "plane", "travel" -> Icons.Default.Flight
        "shoppingcart", "groceries" -> Icons.Default.ShoppingCart
        "graduationcap", "education" -> Icons.Default.School
        "briefcase", "salary" -> Icons.Default.Work
        "trendingup", "investment" -> Icons.Default.TrendingUp
        "laptop", "freelance" -> Icons.Default.Laptop
        "gift" -> Icons.Default.CardGiftcard
        "coffee" -> Icons.Default.LocalCafe
        "home" -> Icons.Default.Home
        "fuel" -> Icons.Default.LocalGasStation
        "phone" -> Icons.Default.Phone
        "wifi" -> Icons.Default.Wifi
        else -> Icons.Default.Sell
    }
}
