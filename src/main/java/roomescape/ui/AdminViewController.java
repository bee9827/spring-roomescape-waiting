package roomescape.ui;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import static roomescape.ui.AdminViewController.BASE_URL;

@Controller
@RequestMapping(BASE_URL)
public class AdminViewController {
    public static final String BASE_URL = "/admin";

    @GetMapping
    public String basic() {
        return "admin/index";
    }

    @GetMapping("/time")
    public String time() {
        return "admin/time";
    }

    @GetMapping("/reservation")
    public String reservation() {
        return "admin/reservation-new";
    }

    @GetMapping("/theme")
    public String theme() {
        return "admin/theme";
    }

}
