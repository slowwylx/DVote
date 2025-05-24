package com.dvote.ui.auth.signin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dvote.R
import com.dvote.ui.theme.AppTypography

@Composable
fun SignInScreen(
    modifier: Modifier = Modifier,
    viewModel: SignInViewModel = hiltViewModel()
) {

    val state = viewModel.signInState.collectAsState().value

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {

        Spacer(modifier = Modifier.fillMaxHeight(.15f))

        Text(text = stringResource(R.string.text_wellcome_to_dvote),
            modifier = Modifier
                .wrapContentSize()
                .align(Alignment.CenterHorizontally),
            color = Color.Black,
            style = AppTypography.headlineLarge
        )

        Spacer(modifier = Modifier.fillMaxHeight(.45f))

        Text(text = stringResource(R.string.text_sign_in_to_continue),
            modifier = Modifier
                .wrapContentSize()
                .align(Alignment.CenterHorizontally),
            color = Color.Black,
            style = AppTypography.headlineMedium
        )

        Spacer(modifier = Modifier.fillMaxHeight(.08f))
        val context = LocalContext.current
        LogInButton(
            modifier = Modifier
                .wrapContentSize()
                .align(Alignment.CenterHorizontally),
            icon = ImageVector.vectorResource(R.drawable.ic_google),
            backgroundColor = Color.Black,
            onClick = {
                viewModel.loginWithGoogle(context)
            },
            isLoading = state is SignInState.Loading
        )
    }
}


@Composable
private fun LogInButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    backgroundColor: Color,
    onClick: () -> Unit,
    isLoading: Boolean = false
) {
    Box(
        modifier = modifier
            .fillMaxSize(0.15f)
            .aspectRatio(1f)
            .clip(shape = RoundedCornerShape(18.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick, enabled = !isLoading)
    ) {
        if (!isLoading) {
            Icon(
                modifier = Modifier
                    .wrapContentSize()
                    .align(Alignment.Center),
                imageVector = icon,
                contentDescription = null,
                tint = Color.Unspecified
            )
        } else {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(28.dp),
                trackColor = Color.Black,
                strokeWidth = 4.dp,
                color = Color.White,
                strokeCap = StrokeCap.Round
            )
        }
    }
}
