package com.bookstore.controller;

import com.bookstore.domain.User;
import com.bookstore.domain.security.PasswordResetToken;
import com.bookstore.domain.security.Role;
import com.bookstore.domain.security.UserRole;
import com.bookstore.service.UserService;
import com.bookstore.service.impl.UserSecurityService;
import com.bookstore.utility.MailConstructor;
import com.bookstore.utility.SecurityUtility;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Controller
public class HomeController {

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private MailConstructor mailConstructor;

    @Autowired
    private UserService userService;
    @Autowired
    private UserSecurityService userSecurityService;

    @RequestMapping("/")
    public String index() {
        return "index";
    }

    @RequestMapping("/login")
    public String login(Model model) {
        model.addAttribute("classActiveLogin", true);
        return "myAccount";
    }

    @RequestMapping(value = "/newUser", method = RequestMethod.POST)
    public String newUserPost(
            HttpServletRequest request,
            @ModelAttribute("email") String userEmail,
            @ModelAttribute("username") String userName,
            Model model
    ) throws Exception {
        model.addAttribute("classActiveNewAccount", true);
        model.addAttribute("email", userEmail);
        model.addAttribute("username", userName);
        if (userService.findByUsername(userName) != null) {
            model.addAttribute("usernameExist", true);
            return "myAccount";
        }
        if (userService.findByEmail(userEmail) != null) {
            model.addAttribute("emailExist", true);
            return "myAccount";
        }
        User user = new User();
        user.setUsername(userName);
        user.setEmail(userEmail);

        String password = SecurityUtility.randomPassword();
        String encryptedPassword = SecurityUtility.passwordEncoder().encode(password);
        user.setPassword(encryptedPassword);

        Role role = new Role();
        role.setRoleId(1);
        role.setName("ROLE_USER");
        Set<UserRole> userRoles = new HashSet<>();
        userRoles.add(new UserRole(user, role));
        userService.createUser(user, userRoles);

        String token = UUID.randomUUID().toString();
        userService.createPasswordResetTokenForUser(user, token);

        String appURL = "http://"
                + request.getServerName() + ":"
                + request.getServerPort()
                + request.getContextPath();

        SimpleMailMessage email = mailConstructor
                .constructResetTokenEmail(appURL, request.getLocale(), token, user, password);
        mailSender.send(email);
        model.addAttribute("emailSent","true");
        return "myAccount";
    }

    @RequestMapping("/newUser")
    public String newUser(Locale locale,
                          @RequestParam("token") String token,
                          Model model) {
        PasswordResetToken passwordToken = userService.getPasswordResetToken(token);
        if (passwordToken == null) {
            String message = "Invalid Token.";
            model.addAttribute("message", message);
            return "redirect:/badRequest";
        }
        User user = passwordToken.getUser();
        String username = user.getUsername();
        UserDetails userDetails = userSecurityService.loadUserByUsername(username);
        Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails,
                userDetails.getPassword(),
                userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        model.addAttribute("user",user);
        model.addAttribute("classActiveEdit", true);
        return "myProfile";
    }

    @RequestMapping("/forgetPassword")
    public String forgetPassword(
            HttpServletRequest request,
            @ModelAttribute("email") String email,
            Model model
    ) {

        model.addAttribute("classActiveForgetPassword", true);

        User user =userService.findByEmail(email);
        if(user == null){
            model.addAttribute("emailDoesNotExist", true);
            return "myAccount";
        }

        String password = SecurityUtility.randomPassword();
        String encryptedPassword = SecurityUtility.passwordEncoder().encode(password);
        user.setPassword(encryptedPassword);

        userService.save(user);

        String token = UUID.randomUUID().toString();
        userService.createPasswordResetTokenForUser(user, token);

        String appURL = "http://"
                + request.getServerName() + ":"
                + request.getServerPort()
                + request.getContextPath();

        SimpleMailMessage forgetPasswordEmail = mailConstructor
                .constructResetTokenEmail(appURL, request.getLocale(), token, user, password);
        mailSender.send(forgetPasswordEmail);
        model.addAttribute("forgetPasswordEmailSent","true");
        return "myAccount";
    }
}
