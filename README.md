# 프로젝트 시작

1. 변경 이력
    - 1.0.0 - 최초 생성 
    - 1.0.1 - customization 한글 인식, mic 입력시 buffer size 문제로 밀리는 문제 조정 
    - 1.0.2 - 화자 분리 및 인식 단어 표시 개선
    - 1.0.3 - websocket 연결 추가 및 인식 단어 표시 개선
2. 사용 전 준비 사항
    1. JAVA 17이상 설치
    2. [www.soniox.com](http://www.soniox.com) 가입 및 API KEY 발급
    사이트 접속 후 우측 Soniox Console 클릭 후, `'Sign up!'`클릭하여 가입 절차 진행
    3. 가입 및 인증 후, https://console.soniox.com/ 접속
    API Keys 탭에 `Create API Key` 후 복사
3. 설정
    1. 총 5개의 preset applcation.properties.~~ 파일이 존재하며 인식을 원하는 설정의 파일을 application.properties 로 변경
        - application.properties : 현재 프로젝트에서 사용하는 설정 파일
        - application.properties.filestream_ko_final : 파일로 스트림으로 인식, 한국어, 완성 문장만 실시간 표기, 용어가중치부여(customization)
        - application.properties.filestream_ko_lowlatency_nonfinal : 파일로 스트림으로 인식, 한국어, 저지연, 용어가중치부여(customization)
        - application.properties.micstream_ko_lowlatency_final : 마이크 스트림으로 인식, 한국어, 저지연, 완성 문장만 실시간 표기, 용어가중치부여(customization) 설정
        - application.properties.micstream_ko_lowlatency_nonfinal : 마이크 스트림으로 인식, 한국어, 저지연, 용어가중치부여(customization) 설정
        - application.properties.micstream_en_lowlatency_speakerdiarization : 파일을 스트림으로 인식, 영어, 저지연, 화자분리 표시 설정, 용어가중치부여(customization) (단, 화자분리는 영어만 가능)
        - application.properties.filestream_en_multichannel : 파일을 스트림으로 인식, 영어, 다중 채널 설정(해당 오디오 파일만 가능)
        - application.properties.micstream_websocket_ko_lowlatency_nonfinal : 마이크 스트림으로 인식, 한국어, 저지연, 용어가중치부여(customization) 설정 (**WebSocket 방식 연결**)
    2. API KEY 입력
    - {root}/conf/application.properties 내 recognition.apiKey의 값에 붙여넣기
    3. proxy 설정 필요시
       - {root}/conf/application.properties 내 다음 설정을 수정 
       ```markdown
       connector.useProxy=true
       connector.proxyHost={proxy ip}
       connector.proxyPort={proxy port}
       ```
4. proto 파일 빌드 및 실행용(`.jar`) 파일 생성
    - Terminal 명령어 : 
    `mvn clean install`
    - IntelliJ : 
    우측 Toolbar → Maven → LifeCycle → install 클릭
        
        추가로 install 실패하는 경우, 해당 프로젝트의 위치가 영어로 되어 있는지 확인(ex. /한글 폴더명/sample-stt-project -> X)
        
5. 실행 방법 (OS별)
    1. windows
        1. run.bat 파일 내 JAVA_HOME 경로 변경 후
        2. run.bat (엔터)
    2. linux 계열
        1. run.sh 파일 내 JAVA_HOME 경로 변경 후
        2. run.sh (엔터)