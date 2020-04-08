import {BrowserModule} from '@angular/platform-browser';
import {NgModule} from '@angular/core';

import {AppComponent} from './app.component';
import {HomeComponent} from './components/home/home.component';
import {LoginComponent} from './components/login/login.component';
import {FormsModule} from '@angular/forms';
import {LoginService} from './services/login.service';
import {ConfigurationService} from './services/configuration.service';
import {StatusService} from './services/status.service';
import {RouterService} from './services/router.service';

@NgModule({
  declarations: [
    AppComponent,
    HomeComponent,
    LoginComponent
  ],
  imports: [
    BrowserModule,
    FormsModule
  ],
  providers: [LoginService, ConfigurationService, StatusService, RouterService],
  bootstrap: [AppComponent]
})
export class AppModule { }
