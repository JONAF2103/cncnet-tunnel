import {Component, EventEmitter, OnInit, Output} from '@angular/core';
import {LoginService} from '../../services/login.service';
import {RouterService} from '../../services/router.service';
import {HomeComponent} from '../home/home.component';

@Component({
  selector: 'app-login',
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.scss']
})
export class LoginComponent implements OnInit {

  title = 'CNC Tunnel - Admin';

  username: string;
  password: string;

  constructor(private loginService: LoginService,
              private routerService: RouterService) { }

  ngOnInit(): void {}

  login(): void {
    this.loginService.login(this.username, this.password).subscribe(response => {
      this.routerService.show(HomeComponent);
    });
  }

}
