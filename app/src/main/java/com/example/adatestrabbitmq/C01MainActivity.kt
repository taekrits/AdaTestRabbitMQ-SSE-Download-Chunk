package com.example.adatestrabbitmq

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.adatestrabbitmq.ui.theme.AdaTestRabbitMQTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AdaTestRabbitMQTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    C01Main()
                }
            }
        }
    }
}

@Composable
fun C01Main() {
    val viewModel: C01MainViewModel = viewModel<C01MainViewModel>()
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text ="RabbitMQ",
            fontSize = 24.sp,
            modifier = Modifier
                .padding(bottom = 20.dp, top = 20.dp)
                .align(CenterHorizontally)
        )

        Text(
            text = viewModel.atC_MessageLiveDataRabbitMQ.toString(),
            fontSize = 24.sp,
            modifier = Modifier
                .padding(bottom = 20.dp, top = 20.dp)
                .align(CenterHorizontally)
        )
        Row {
            Button(
                onClick = {
                    viewModel.C_GETxRabbitMQConnect()
                          },
                modifier = Modifier.weight(1f)
            )

            {

                Text(text = "Connect")

            }
        }
        Text(
            text ="Server Sent Event",
            fontSize = 24.sp,
            modifier = Modifier
                .padding(bottom = 20.dp, top = 20.dp)
                .align(CenterHorizontally)
        )

        Text(
            text = viewModel.atC_MessageLiveDataSSE.toString(),
            fontSize = 24.sp,
            modifier = Modifier
                .padding(bottom = 20.dp, top = 20.dp)
                .align(CenterHorizontally)
        )
        Row {
            Button(
                onClick = {
                    viewModel.C_GetxConnectToSSE()
                },
                modifier = Modifier.weight(1f)
            )

            {

                Text(text = "Start")

            }
            Button(onClick = {

                    viewModel.C_SETxCancle()

            },
                modifier = Modifier.weight(1f)
            )
            {

                Text(text = "STOP")

            }
        }

    }
}


@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    AdaTestRabbitMQTheme {
        C01Main()
    }
}