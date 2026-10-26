package com.example.savedog.Vision.detector;

// YOLO 공개 모델이 배운 COCO 데이터셋의 물체 80종. 모델 출력의 순서와 같다.
final class CocoLabels {
    static final String[] ENGLISH = {
            "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat", "traffic light",
            "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat", "dog", "horse", "sheep", "cow",
            "elephant", "bear", "zebra", "giraffe", "backpack", "umbrella", "handbag", "tie", "suitcase", "frisbee",
            "skis", "snowboard", "sports ball", "kite", "baseball bat", "baseball glove", "skateboard", "surfboard",
            "tennis racket", "bottle", "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple",
            "sandwich", "orange", "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair", "couch",
            "potted plant", "bed", "dining table", "toilet", "tv", "laptop", "mouse", "remote", "keyboard",
            "cell phone", "microwave", "oven", "toaster", "sink", "refrigerator", "book", "clock", "vase",
            "scissors", "teddy bear", "hair drier", "toothbrush"
    };

    static final String[] KOREAN = {
            "사람", "자전거", "자동차", "오토바이", "비행기", "버스", "기차", "트럭", "보트", "신호등",
            "소화전", "정지 표지판", "주차 요금기", "벤치", "새", "고양이", "개", "말", "양", "소",
            "코끼리", "곰", "얼룩말", "기린", "배낭", "우산", "핸드백", "넥타이", "여행 가방", "원반",
            "스키", "스노보드", "공", "연", "야구 방망이", "야구 글러브", "스케이트보드", "서핑보드",
            "테니스 라켓", "병", "와인잔", "컵", "포크", "칼", "숟가락", "그릇", "바나나", "사과",
            "샌드위치", "오렌지", "브로콜리", "당근", "핫도그", "피자", "도넛", "케이크", "의자", "소파",
            "화분", "침대", "식탁", "변기", "텔레비전", "노트북", "마우스", "리모컨", "키보드",
            "휴대폰", "전자레인지", "오븐", "토스터", "싱크대", "냉장고", "책", "시계", "꽃병",
            "가위", "곰 인형", "헤어드라이어", "칫솔"
    };

    static final int COUNT = ENGLISH.length;

    private CocoLabels() {
    }
}
