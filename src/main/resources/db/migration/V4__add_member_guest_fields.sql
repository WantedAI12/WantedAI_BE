-- 게스트 모드(로그인 없이 체험) 계정 표시. 만료·삭제 없이 일반 회원과 동일하게 무기한
-- 유지된다 - 게스트 출처 데이터를 구분하기 위한 플래그일 뿐이다.
alter table members
    add column is_guest bit not null default 0;
