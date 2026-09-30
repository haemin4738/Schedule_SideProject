// 백엔드 SignupRequest 와 같은 규칙: 영문/숫자/특수문자(ASCII 구두점)를 각각 1자 이상 포함한 9~15자
export const PASSWORD_PATTERN = /^(?=.*[A-Za-z])(?=.*\d)(?=.*[!-/:-@[-`{-~])[A-Za-z\d!-/:-@[-`{-~]{9,15}$/
export const PASSWORD_RULE_MESSAGE = '비밀번호는 영문, 숫자, 특수문자를 각각 1자 이상 포함한 9~15자여야 합니다.'
