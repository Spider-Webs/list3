package bucket.list.service.Member;

import bucket.list.domain.Member;
import bucket.list.dto.MailDto;
import bucket.list.dto.OAuthAttributes;
import bucket.list.dto.SecurityMember;
import bucket.list.dto.SessionMember;
import bucket.list.repository.Member.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpSession;
import java.util.Collections;
import java.util.Optional;

@RequiredArgsConstructor
@Transactional
@Service
public class MemberService implements UserDetailsService, OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final MemberRepository memberRepository;
    private final HttpSession httpSession;
    private final JavaMailSender javaMailSender;


    public Member saveMember(Member member){
//        memberExist(member);
        return memberRepository.save(member);
    }

//    private void memberExist(Member member){ // 중복가입확인여부 메서드
//        Optional<Member> findMember = memberRepository.findByMemberId(member.getMemberId());
//        if(findMember!=null){
//            throw new IllegalStateException("이미 가입된 회원입니다");
//        }
//    }

    //데이터 베이스에서 회원정보를 가져오는 UserDetailService인터페이스에 구현 메서드
    @Override
    public UserDetails loadUserByUsername(String memberId) throws UsernameNotFoundException {

        Optional<Member> member = memberRepository.findByMemberId(memberId);

        if (!member.isPresent()) throw new UsernameNotFoundException("존재하지 않는 Id 입니다.");

        return new SecurityMember(member.get());

        //변경 전
//        return User.builder()
//                .username(member.getMemberId())
//                .password(member.getMemberPassword())
//                .roles(member.getRole().toString())
//                .build();


    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate = new DefaultOAuth2UserService();

        OAuth2User oAuth2User = delegate.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        String userNameAttributeName = userRequest.getClientRegistration()
                .getProviderDetails()
                .getUserInfoEndpoint()
                .getUserNameAttributeName();

        OAuthAttributes attributes = OAuthAttributes.
                of(registrationId, userNameAttributeName, oAuth2User.getAttributes());

        Member member = saveOrUpdate(attributes);

        httpSession.setAttribute("member", new SessionMember(member));


        return new DefaultOAuth2User(Collections.singleton(new SimpleGrantedAuthority(member.getRole().getKey())),
                attributes.getAttributes(),
                attributes.getNameAttributeKey());
    }

    private Member saveOrUpdate(OAuthAttributes attributes){
        Member member = memberRepository.findByMemberEmail(attributes.getMemberEmail())
                .map(entity -> entity.update(attributes.getMemberName(), attributes.getPicture()))
                .orElse(attributes.toEntity());


        return memberRepository.save(member);
    }
    //비밀번호 찾기시 기재한 이메일과, 가입당시 이메일 일치하는지 확인하는 메서드
    public void equalEmail(String memberEmail){
        Optional<Member> byMemberEmail = memberRepository.findByMemberEmail(memberEmail);
        
        if(byMemberEmail.isEmpty()){

            throw new IllegalStateException("가입되어있지않는 이메일입니다 다시 입력해주세요");
        }
    }

    //임시비밀번호 생성 및 회원 비밀번호 변경
    public MailDto changePassword(String memberEmail){
        String tempPassword = getTempPassword();
        MailDto dto = new MailDto();
        dto.setAddress(memberEmail);
        dto.setTitle("버킷리스트 임시비밀번호 발송 이메일입니다");
        dto.setMessage("안녕하세요. 버킷리스트 임시비밀번호 안내 관련 이메일 입니다." + " 회원님의 임시 비밀번호는 "
                + tempPassword + " 입니다." + "임시 비밀번호로 로그인 후에 비밀번호를 변경을 해주세요");
        updatePassword(tempPassword,memberEmail);
        return dto;
    }
    //임시비밀번호로 업데이트
    public void updatePassword(String tempPassword, String memberEmail){
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        String memberPassword = passwordEncoder.encode(tempPassword);
        Long memberId = memberRepository.findByMemberEmail(memberEmail).get().getMemberIdx();
        memberRepository.updatePassword(memberId,memberPassword);
    }
    //메일 발송
    public void mailSend(MailDto mailDto) {
        System.out.println("전송 완료!");
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(mailDto.getAddress());
        message.setSubject(mailDto.getTitle());
        message.setText(mailDto.getMessage());
        message.setFrom("dyko3786@gmail.com");
        message.setReplyTo("dyko3786@gmail.com");
        System.out.println("message"+message);
        javaMailSender.send(message);
    }


    //랜덤함수로 임시비밀번호 구문 만들기
    public String getTempPassword(){
        char[] charSet = new char[] { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F',
                'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z' };

        String str = "";

        // 문자 배열 길이의 값을 랜덤으로 10개를 뽑아 구문을 작성함
        int idx = 0;
        for (int i = 0; i < 10; i++) {
            idx = (int) (charSet.length * Math.random());
            str += charSet[idx];
        }
        return str;
    }
}
