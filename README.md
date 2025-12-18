BusArrival

Android 대중교통 길찾기 앱

프로젝트 소개

BusArrival는 SK Open API(Tmap)를 사용하여
출발지와 도착지를 입력하면 대중교통 경로를 검색하고
소요 시간, 거리, 요금, 환승 정보를 보여주는 Android 앱입니다.

Jetpack Compose와 MVVM 구조로 구현되었습니다.

주요 기능

출발지 / 도착지 장소 검색

대중교통 경로 검색 (버스, 지하철, 도보)

소요 시간, 거리, 요금, 환승 횟수 표시

구간별 이동 정보 제공

기술 스택

Kotlin

Jetpack Compose (Material3)

MVVM, ViewModel

Coroutines, StateFlow

Retrofit2, OkHttp, Gson

SK Open API (Tmap)

사용 API

장소 검색 API
GET /tmap/pois

대중교통 경로 검색 API
POST /transit/routes

프로젝트 구조
com.example.busarrival
├── MainActivity.kt
├── TransitViewModel.kt
├── TransitApiService.kt
├── model
└── ui

실행 방법

SK Open API 키 발급

MainActivity.kt에 API 키 입력

private val APP_KEY = "YOUR_API_KEY"


앱 실행

권한 설정
<uses-permission android:name="android.permission.INTERNET"/>

개발 목적

Jetpack Compose 학습

REST API 연동 실습

MVVM 아키텍처 이해
