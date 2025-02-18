package com.dvote.ui.auth.signin

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dvote.R

@Composable
fun SignInScreen(
    modifier: Modifier = Modifier,
    viewModel: SignInViewModel = hiltViewModel()
) {
//    val gradientColors = listOf(
//        Color(0xFFFFF4E5),
//        Color(0xFFFCC89B)
//    )
    val infiniteTransition = rememberInfiniteTransition(label = "")

    val color1 by infiniteTransition.animateColor(
        initialValue = Color(0xFFFCECD8), // Start with warm cream
        targetValue = Color(0xFFFFCB9E),  // Transition to gentle apricot
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000), // Duration of transition
            repeatMode = RepeatMode.Reverse
        ), label = ""
    )

    val color2 by infiniteTransition.animateColor(
        initialValue = Color(0xFFFFCB9E),
        targetValue = Color(0xFFFCECD8),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000),
            repeatMode = RepeatMode.Reverse
        ), label = ""
    )


    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = Brush.verticalGradient(listOf(color1, color2)))
            .then(modifier),
        verticalArrangement = Arrangement.Center
    ) {
        LogInButton(
            modifier = Modifier
                .wrapContentSize()
                .align(Alignment.CenterHorizontally),
            icon = painterResource(R.drawable.ic_diia_ua_text),
            backgroundColor = Color.Black,
            onClick = {
                viewModel.loginWithDiia()
            }
        )
    }
}


@Composable
private fun LogInButton(
    modifier: Modifier = Modifier,
    icon: Painter,
    backgroundColor: Color,
    onClick: () -> Unit
) {
    Box(modifier = modifier
        .wrapContentSize()
        .clip(shape = RoundedCornerShape(18.dp))
        .background(backgroundColor)
        .clickable { onClick() })
    {
        Icon(
            modifier = Modifier
                .requiredSize(62.dp)
                .padding(12.dp),
            painter = icon,
            contentDescription = null,
            tint = Color.White,
        )
    }

}